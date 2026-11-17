package com.example.project.legal;

import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="legal.ai.auto-start",havingValue="true")
public class LocalAiLifecycle implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(LocalAiLifecycle.class);
    private final IntegrationSettings settings;
    private Process process;

    public LocalAiLifecycle(IntegrationSettings settings) {
        this.settings=settings;
    }

    @Override
    public synchronized void run(ApplicationArguments args) throws IOException {
        if(!settings.localAi()||!settings.aiReady())return;
        // A bound port also covers a model that is still loading.
        try(var socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",11435),500);
            log.info("Local AI is already running. Reusing the existing server.");
            return;
        } catch(IOException unavailable) {
            // Start the installed runtime below.
        }
        Path runtime=Path.of(settings.value("legal.ai.runtime-path",
            Path.of(System.getProperty("user.home"),"Documents","Codex","local-legal-ai").toString()));
        Path script=Path.of(settings.value("legal.ai.server-script","local-ai/serve.py")).toAbsolutePath();
        Path python=runtime.resolve("python-portable/python.exe");
        for(Path required:new Path[]{python,script,runtime.resolve("model/config.json"),
                runtime.resolve("training/adapter/adapter_config.json"),runtime.resolve("api-token.txt")}) {
            if(!Files.isRegularFile(required))throw new IOException("Local AI runtime file missing: "+required);
        }
        var builder=new ProcessBuilder(python.toString(),script.toString(),
            "--base",runtime.resolve("model").toString(),
            "--adapter",runtime.resolve("training/adapter").toString(),
            "--token-file",runtime.resolve("api-token.txt").toString());
        builder.environment().put("HF_HUB_OFFLINE","1");
        builder.environment().put("TRANSFORMERS_OFFLINE","1");
        builder.environment().put("HF_HUB_DISABLE_TELEMETRY","1");
        builder.environment().put("PYTHONUTF8","1");
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(runtime.resolve("server.log").toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(runtime.resolve("server.err").toFile()));
        process=builder.start();
        log.info("Starting local AI, PID {}. Model loading may take a few seconds.",process.pid());
        process.onExit().thenAccept(exited->log.info("Local AI exited with code {}.",exited.exitValue()));
    }

    @PreDestroy
    public synchronized void stop() {
        if(process!=null&&process.isAlive()) {
            var children=process.descendants().toList();
            children.forEach(ProcessHandle::destroy);
            process.destroy();
            try {
                if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS))process.destroyForcibly();
                children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            } catch(InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
