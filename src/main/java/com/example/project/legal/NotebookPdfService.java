package com.example.project.legal;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotebookPdfService {
    public static final long MAX_UPLOAD=10*1024*1024;
    private static final int MAX_TEXT=20000;
    private final NotebookService notebooks;
    private final TimelineRepository timeline;
    private final ChecklistRepository checklist;
    private final BillingService billing;
    private final ConsultationService consultation;
    private final Semaphore slots=new Semaphore(2);
    public NotebookPdfService(NotebookService notebooks,TimelineRepository timeline,ChecklistRepository checklist,BillingService billing,ConsultationService consultation) {
        this.notebooks=notebooks;this.timeline=timeline;this.checklist=checklist;this.billing=billing;this.consultation=consultation;
    }
    public record Preview(long notebookId,String notebookTitle,String text,int pages,String saveText) {}
    public Preview extract(String user,long id,MultipartFile file) {
        var book=notebooks.owned(user,id);
        LegalService.require(!file.isEmpty()&&file.getSize()<=MAX_UPLOAD,"PDF는 10MB 이하로 업로드해 주세요.");
        acquire();
        try {
            byte[] bytes=file.getBytes();
            LegalService.require(bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"),"올바른 PDF 파일을 선택해 주세요.");
            try(var doc=Loader.loadPDF(bytes)) {
                LegalService.require(!doc.isEncrypted(),"암호화되지 않은 PDF로 다시 시도해 주세요.");
                LegalService.require(doc.getNumberOfPages()>0&&doc.getNumberOfPages()<=100,"100페이지 이하 PDF를 선택해 주세요.");
                var stripper=new PDFTextStripper() {
                    private int extracted;
                    @Override protected void writeString(String text,List<TextPosition> positions)throws IOException {
                        extracted+=text.length();if(extracted>MAX_TEXT)throw new TextLimitException();
                        super.writeString(text,positions);
                    }
                };
                stripper.setSortByPosition(true);
                String text=stripper.getText(doc).strip();
                LegalService.require(!text.isBlank(),"추출할 글자가 없습니다. 스캔 PDF는 OCR 처리 후 업로드해 주세요.");
                LegalService.require(text.length()<=MAX_TEXT,"추출 내용은 20,000자 이하만 지원합니다. 문서를 나눠 주세요.");
                return new Preview(id,book.title,text,doc.getNumberOfPages(),text.substring(0,Math.min(2000,text.length())));
            }
        } catch(TextLimitException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"추출 내용은 20,000자 이하만 지원합니다. 문서를 나눠 주세요."); }
          catch(IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF를 읽을 수 없습니다. 암호·손상 여부를 확인해 주세요."); }
        finally { slots.release(); }
    }
    private static class TextLimitException extends IOException {}
    public byte[] export(String user,long id) {
        return export(user,id,PdfSelection.NOTEBOOK,true);
    }
    public byte[] export(String user,long id,Set<String> sections,boolean notes) {
        var book=notebooks.owned(user,id);
        billing.requirePremium(user);
        var lines=new ArrayList<String>();
        lines.add("판결읽기 | 나의 사건 노트");lines.add(book.title);lines.add("분야: "+book.category+" / 비공개 자료 / 출력일: "+LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        lines.add("이 문서는 사용자가 기록한 자료이며 법률적 판단이나 증명서를 의미하지 않습니다.");
        lines.add(notes?"상세 메모 포함":"상세 메모 제외");
        if(sections.contains("timeline")){lines.add("");lines.add("사건 타임라인");
        var events=timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(id);
        if(events.isEmpty())lines.add("아직 기록이 없습니다.");
        for(var e:events){lines.add(e.occurredOn+" ["+e.kind+"] "+e.title);if(notes)lines.add(e.note);lines.add("");}}
        if(sections.contains("checklist")){
        lines.add("준비 자료 체크리스트");
        for(var item:checklist.findByNotebookIdOrderByIdAsc(id))lines.add((item.completed?"[확인] ":"[미확인] ")+item.label);}
        if(sections.contains("followups"))appendFollowups(lines,consultation.prepare(user,id).followups(),notes);
        return renderLimited(lines);
    }
    public byte[] exportConsultation(String user,long id) {
        return exportConsultation(user,id,PdfSelection.CONSULTATION,true);
    }
    public byte[] exportConsultation(String user,long id,Set<String> sections,boolean notes) {
        var prep=consultation.prepare(user,id);
        billing.requirePremium(user);
        var lines=new ArrayList<String>();
        lines.add("판결읽기 | 상담 준비 자료");lines.add(prep.title());
        lines.add("분야: "+prep.category()+" / 작성일: "+LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
        lines.add("사용자가 작성한 상담 준비용 기록입니다. 사실과 추측을 구분하고 상담 시 내용을 확인하세요.");
        lines.add(notes?"상세 메모 포함":"상세 메모 제외");
        if(sections.contains("summary")){lines.add("");lines.add("사건 요약");lines.add(orEmpty(prep.summary()));}
        if(sections.contains("goal")){lines.add("");lines.add("상담에서 확인하고 싶은 점");lines.add(orEmpty(prep.goal()));}
        if(sections.contains("questions")){lines.add("");lines.add("상담 질문");lines.add(orEmpty(prep.questions()));}
        if(sections.contains("timeline")){lines.add("");lines.add("사건 경위 - 날짜순 기록");
        if(prep.events().isEmpty())lines.add("아직 기록이 없습니다.");
        for(var e:prep.events()){lines.add(e.date()+" ["+e.kind()+"] "+e.title());if(notes)lines.add(e.note());lines.add("");}}
        if(sections.contains("checklist")){
        lines.add("준비 자료 - "+prep.completed()+" / "+prep.items().size()+" 항목 확인");
        if(prep.items().isEmpty())lines.add("등록된 항목이 없습니다.");
        for(var item:prep.items())lines.add((item.completed()?"[확인] ":"[미확인] ")+item.label());}
        if(sections.contains("source")){
        lines.add("");lines.add("참고 자료");
        if(prep.source()==null)lines.add("연결된 공개 참고 자료가 없습니다.");
        else {
            var source=prep.source();lines.add(source.title());
            if(source.demo())lines.add("가상 학습 사례 - 실제 판결이 아닙니다.");
            else {lines.add("사건번호: "+source.number());lines.add("공식 출처: "+source.url());}
            lines.add("참고 사례와 내 사건의 사실관계는 다를 수 있습니다.");
        }}
        if(sections.contains("followups"))appendFollowups(lines,prep.followups(),notes);
        return renderLimited(lines);
    }
    private void appendFollowups(List<String> lines,List<ConsultationService.Followup> logs,boolean notes){
        lines.add("");lines.add("상담 후 기록·다음 할 일");if(logs.isEmpty())lines.add("아직 상담 후 기록이 없습니다.");
        for(var log:logs){lines.add(log.date()+" / 후속 조치 "+(log.completed()?"완료":"진행 중"));if(notes)lines.add("상담 답변: "+log.answer());lines.add("다음 할 일: "+log.nextActions());lines.add("");}
    }
    private String orEmpty(String value){return value.isBlank()?"아직 작성하지 않았습니다.":value;}
    private byte[] renderLimited(List<String> lines) {
        LegalService.require(lines.stream().mapToLong(String::length).sum()<=300000,"출력할 기록이 너무 많습니다. 노트를 나눠 주세요.");
        acquire();
        try {return render(lines);} catch(IOException e) {throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"PDF를 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");}
        finally {slots.release();}
    }
    private void acquire() {if(!slots.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"PDF 처리 중입니다. 잠시 후 다시 시도해 주세요.");}
    byte[] render(List<String> paragraphs)throws IOException {
        try(var doc=new PDDocument();var fontInput=new ClassPathResource("fonts/NanumGothic-Regular.ttf").getInputStream();var output=new ByteArrayOutputStream()) {
            var font=PDType0Font.load(doc,fontInput,true);
            try(var writer=new PageWriter(doc,font)) {
                for(String paragraph:paragraphs)for(String line:paragraph.split("\\R",-1)) {
                    var buffer=new StringBuilder();float width=0;
                    for(int cp:line.codePoints().toArray()) {
                        if(Character.isISOControl(cp))cp=' ';
                        String glyph=new String(Character.toChars(cp));
                        try{font.encode(glyph);}catch(IllegalArgumentException unsupported){glyph="?";}
                        float next=font.getStringWidth(glyph)/1000*11;
                        if(width+next>PDRectangle.A4.getWidth()-100&&!buffer.isEmpty()){writer.line(buffer.toString());buffer.setLength(0);width=0;}
                        buffer.append(glyph);width+=next;
                    }
                    writer.line(buffer.toString());
                }
            }
            doc.getDocumentInformation().setTitle("판결읽기 - 사건 노트");doc.save(output);return output.toByteArray();
        }
    }
    private static class PageWriter implements AutoCloseable {
        private final PDDocument doc;private final PDType0Font font;private PDPageContentStream stream;private float y;
        PageWriter(PDDocument doc,PDType0Font font)throws IOException{this.doc=doc;this.font=font;page();}
        private void page()throws IOException {
            if(stream!=null)stream.close();
            LegalService.require(doc.getNumberOfPages()<150,"최대 150페이지까지 내보낼 수 있습니다. 노트를 나눠 주세요.");
            var page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);y=PDRectangle.A4.getHeight()-55;
            stream.beginText();stream.setFont(font,9);stream.newLineAtOffset(50,28);stream.showText("판결읽기 | "+doc.getNumberOfPages());stream.endText();
        }
        void line(String text)throws IOException{if(y<60)page();stream.beginText();stream.setFont(font,11);stream.newLineAtOffset(50,y);stream.showText(text);stream.endText();y-=18;}
        @Override public void close()throws IOException{if(stream!=null)stream.close();}
    }
}
