package com.example.project.legal;

import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integrations-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
    "legal.ai.enabled=true","legal.ai.api-key=fake-test-key","legal.ai.daily-limit=2","legal.billing.enabled=true","legal.billing.enforce-premium=false",
    "legal.billing.store-id=store-test","legal.billing.channel-key=channel-test","legal.billing.api-secret=fake-secret","legal.billing.webhook-secret=dGVzdC1zZWNyZXQ="})
@AutoConfigureMockMvc
class IntegrationFeaturesTest {
    @Autowired MockMvc mvc;@Autowired NotebookService notebooks;@Autowired NotebookPdfService pdf;@Autowired CaseRepository cases;
    @Autowired AiUsageRepository usage;@Autowired PaymentOrderRepository orders;@Autowired BillingService billing;
    @MockitoBean AiGateway ai;@MockitoBean PaymentGateway payments;
    @BeforeEach void resetData() {usage.deleteAll();orders.deleteAll();reset(ai,payments);}
    long realCase() {return cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->!c.demo).findFirst().orElseThrow().id;}
    @Test void aiRequiresLoginCsrfConsentAndUsesOnlyPublicCase()throws Exception {
        long id=realCase();
        mvc.perform(post("/cases/"+id+"/ai").with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/cases/"+id+"/ai").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(post("/cases/"+id+"/ai").with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
        when(ai.explain(anyString(),anyString())).thenReturn("안전한 설명 <script>alert(1)</script>");
        mvc.perform(post("/cases/"+id+"/ai").with(user("alice")).with(csrf()).param("consent","true"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("AI 생성 설명"))).andExpect(content().string(containsString("&lt;script&gt;")));
        var context=org.mockito.ArgumentCaptor.forClass(String.class);verify(ai).explain(anyString(),context.capture());
        assertThat(context.getValue()).contains("원문 발췌","공식 출처").doesNotContain("나의 사건 노트");
    }
    @Test void aiRejectsDemoPrivateCaseAndUnsupportedMode()throws Exception {
        var demo=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->c.demo).findFirst().orElseThrow();
        mvc.perform(post("/cases/"+demo.id+"/ai").with(user("alice")).with(csrf()).param("consent","true")).andExpect(status().isBadRequest());
        var draft=new LegalModels.CaseEntry();draft.title="private";draft.category="주거";draft.published=false;cases.saveAndFlush(draft);
        mvc.perform(post("/cases/"+draft.id+"/ai").with(user("admin").roles("ADMIN")).with(csrf()).param("consent","true")).andExpect(status().isNotFound());
        mvc.perform(post("/cases/"+realCase()+"/ai").with(user("alice")).with(csrf()).param("consent","true").param("mode","arbitrary instructions")).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }
    @Test void aiQuotaPersistsAndProviderErrorsDoNotLeakSecrets()throws Exception {
        when(ai.explain(anyString(),anyString())).thenThrow(new IllegalStateException("secret-fake-key"));
        for(int i=0;i<2;i++)mvc.perform(post("/cases/"+realCase()+"/ai").with(user("alice")).with(csrf()).param("consent","true")).andExpect(status().isBadGateway()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-fake-key"))));
        mvc.perform(post("/cases/"+realCase()+"/ai").with(user("alice")).with(csrf()).param("consent","true")).andExpect(status().isTooManyRequests());
        verify(ai,times(2)).explain(anyString(),anyString());
    }
    @Test void pdfExportsKoreanAndWrapsLongRecordsAcrossPages()throws Exception {
        var book=notebooks.create("alice","보증금 사건 기록","주거",null);
        for(int i=0;i<8;i++)notebooks.addEvent("alice",book.id,LocalDate.of(2026,1,1),"기타","기록 "+i,"계약서와 입금 내용을 확인했습니다. ".repeat(40));
        var result=mvc.perform(get("/notebooks/"+book.id+"/pdf").with(user("alice"))).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM)).andExpect(header().string("Cache-Control","no-store")).andReturn();
        byte[] bytes=com.example.project.security.DataCipher.decryptFile(result.getResponse().getContentAsByteArray());
        try(var doc=Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(doc)).contains("보증금 사건 기록","입금 내용을 확인했습니다","준비 자료 체크리스트");
            Files.createDirectories(Path.of("target/pdf-review"));Files.write(Path.of("target/pdf-review/notebook.pdf"),bytes);
            var renderer=new PDFRenderer(doc);ImageIO.write(renderer.renderImageWithDPI(0,110),"png",Path.of("target/pdf-review/page-1.png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(doc.getNumberOfPages()-1,110),"png",Path.of("target/pdf-review/page-last.png").toFile());
        }
    }
    @Test void pdfOwnershipIsCheckedForDownloadAndUploadEvenForAdmin()throws Exception {
        var book=notebooks.create("alice","private","주거",null);
        mvc.perform(get("/notebooks/"+book.id+"/pdf")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/notebooks/"+book.id+"/pdf").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(multipart("/notebooks/"+book.id+"/pdf-preview").file(new MockMultipartFile("file","file.pdf","application/pdf","%PDF-abc".getBytes())).with(user("bob")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void pdfImportShowsPreviewWithoutAutomaticallySavingAnEvent()throws Exception {
        var book=notebooks.create("alice","PDF 노트","주거",null);byte[] bytes=pdf.render(List.of("계약서 확인 기록","한글 내용을 추출합니다."));
        mvc.perform(multipart("/notebooks/"+book.id+"/pdf-preview").file(new MockMultipartFile("file","source.pdf","application/pdf",bytes)).with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/notebooks/"+book.id+"/pdf-preview").file(new MockMultipartFile("file","source.pdf","application/pdf",bytes)).with(user("alice")).with(csrf())).andExpect(status().isOk()).andExpect(content().string(containsString("한글 내용을 추출합니다")));
        mvc.perform(get("/notebooks/"+book.id).with(user("alice"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("한글 내용을 추출합니다"))));
    }
    @Test void invalidAndOversizedPdfAreRejected()throws Exception {
        var book=notebooks.create("alice","PDF 노트","주거",null);
        for(byte[] bytes:List.of("not a PDF".getBytes(),"%PDF-corrupted".getBytes(),new byte[(int)NotebookPdfService.MAX_UPLOAD+1]))
            mvc.perform(multipart("/notebooks/"+book.id+"/pdf-preview").file(new MockMultipartFile("file","x.pdf","application/pdf",bytes)).with(user("alice")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void paymentUsesServerPriceAndOwnerBoundIdempotentVerification()throws Exception {
        mvc.perform(post("/billing/orders").with(user("alice"))).andExpect(status().isForbidden());
        mvc.perform(post("/billing/orders").with(user("alice")).with(csrf()).param("amount","1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(9900)).andExpect(jsonPath("$.apiSecret").doesNotExist());
        var order=orders.findTop20ByUsernameOrderByCreatedAtDesc("alice").get(0);
        mvc.perform(post("/billing/verify").with(user("bob")).with(csrf()).param("paymentId",order.paymentId)).andExpect(status().isNotFound());verifyNoInteractions(payments);
        Instant paid=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);when(payments.fetch(order.paymentId)).thenReturn(new PaymentGateway.Snapshot(order.paymentId,"store-test","PAID",9900,"KRW",paid));
        for(int i=0;i<2;i++)mvc.perform(post("/billing/verify").with(user("alice")).with(csrf()).param("paymentId",order.paymentId)).andExpect(redirectedUrl("/billing"));
        assertThat(orders.findById(order.paymentId).orElseThrow().validUntil).isEqualTo(paid.plus(Duration.ofDays(30)));
        mvc.perform(get("/billing").with(user("alice"))).andExpect(status().isOk()).andExpect(content().string(containsString("이용권 만료")));
    }
    @Test void mismatchedPendingAndCancelledPaymentsDoNotGrantAccess()throws Exception {
        var checkout=billing.create("alice");String id=checkout.paymentId();
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","PAID",1,"KRW",Instant.now()));
        mvc.perform(post("/billing/verify").with(user("alice")).with(csrf()).param("paymentId",id)).andExpect(status().isConflict());assertThat(billing.activeUntil("alice")).isNull();
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"other-store","PAID",9900,"KRW",Instant.now()));
        mvc.perform(post("/billing/verify").with(user("alice")).with(csrf()).param("paymentId",id)).andExpect(status().isConflict());
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","PENDING",9900,"KRW",null));billing.verify("alice",id);assertThat(billing.activeUntil("alice")).isNull();
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","PAID",9900,"KRW",Instant.now()));billing.verify("alice",id);assertThat(billing.activeUntil("alice")).isNotNull();
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","CANCELLED",9900,"KRW",null));billing.verify("alice",id);assertThat(billing.activeUntil("alice")).isNull();
    }
    @Test void signedWebhookRechecksProviderAndIsIdempotent()throws Exception {
        var checkout=billing.create("alice");String id=checkout.paymentId();when(payments.verifyWebhook(anyString(),any(),any(),any())).thenReturn(id);
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","PAID",9900,"KRW",Instant.now()));
        mvc.perform(post("/billing/webhook").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());assertThat(billing.activeUntil("alice")).isNotNull();
        when(payments.fetch(id)).thenReturn(new PaymentGateway.Snapshot(id,"store-test","CANCELLED",9900,"KRW",null));
        mvc.perform(post("/billing/webhook").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNoContent());assertThat(billing.activeUntil("alice")).isNull();
    }
}
