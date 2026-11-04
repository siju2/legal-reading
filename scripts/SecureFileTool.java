import com.example.project.security.DataCipher;
import java.nio.file.*;
public class SecureFileTool {
    public static void main(String[] args)throws Exception {
        if(args.length!=3||!java.util.Set.of("encrypt","decrypt").contains(args[0]))throw new IllegalArgumentException("Usage: encrypt|decrypt INPUT OUTPUT");
        Path input=Path.of(args[1]),output=Path.of(args[2]);
        byte[] bytes=Files.readAllBytes(input);
        byte[] result=args[0].equals("encrypt")?DataCipher.encryptFile(bytes):DataCipher.decryptFile(bytes);
        Files.write(output,result,StandardOpenOption.CREATE_NEW);
        System.out.println("Completed: "+output.getFileName());
    }
}
