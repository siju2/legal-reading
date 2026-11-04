import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

class ExpandProjectDemo {
    static String resolve(String value) {
        var pattern=java.util.regex.Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");
        var matcher=pattern.matcher(value);var out=new StringBuffer();
        while(matcher.find()) {String env=System.getenv(matcher.group(1));matcher.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(env!=null?env:Objects.toString(matcher.group(2),"")));}
        matcher.appendTail(out);return out.toString();
    }
    public static void main(String[] args)throws Exception {
        Properties config=new Properties();Path resources=Path.of(args[0],"src/main/resources");
        for(String name:List.of("application.properties","application-local.properties")) {
            Path file=resources.resolve(name);if(Files.exists(file))try(var reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){config.load(reader);}
        }
        String url=Objects.toString(System.getenv("SPRING_DATASOURCE_URL"),resolve(config.getProperty("spring.datasource.url")));
        if(!url.matches("^jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/.*"))throw new IllegalArgumentException("Demo seeding is restricted to local MySQL.");
        String user=Objects.toString(System.getenv("SPRING_DATASOURCE_USERNAME"),resolve(config.getProperty("spring.datasource.username","project_app")));
        String password=Objects.toString(System.getenv("SPRING_DATASOURCE_PASSWORD"),resolve(config.getProperty("spring.datasource.password","")));
        Properties auth=new Properties();auth.setProperty("user",user);auth.setProperty("password",password);auth.setProperty("connectTimeout","5000");auth.setProperty("socketTimeout","5000");
        DriverManager.setLoginTimeout(5);
        try(Connection c=DriverManager.getConnection(url,auth)) {
            c.setAutoCommit(false);
            try {
                String owner="demo_legal";
                try(var s=c.prepareStatement("SELECT COUNT(*) FROM members WHERE username=?")){s.setString(1,owner);try(var r=s.executeQuery()){r.next();if(r.getLong(1)!=1)throw new IllegalStateException("Demo account missing");}}
                String[] categories={"주거","일자리","돈·거래","온라인 생활","일상 사고"};
                String[][] topics={
                    {"보증금 반환","임대차 종료 통지","전세 정산 자료","주택 수리 기록","이사 및 인도 일정"},
                    {"상여금 지급 조건","연장근로 기록","퇴직 급여 자료","근로계약 확인","급여명세 비교"},
                    {"중고 물품 하자","대여금 약정","거래 취소 연락","물품 인수 기록","송금 내역 정리"},
                    {"게시물 공개 범위","게시글 수정 이력","온라인 거래 대화","계정 도용 경위","사진 게시 동의"},
                    {"시설 이용 사고","이동 중 사고","물품 파손 경위","수리비 지출","현장 사진 정리"}
                };
                String[][] labels={
                    {"임대차계약서","특약 확인","종료 통지","수신 기록","입금 내역","인도 사진","정산 내역","질문 목록"},
                    {"근로계약서","취업규칙","급여명세서","급여 입금 내역","출퇴근 기록","상여금 규정","업무 대화","질문 목록"},
                    {"약정서","판매 설명","거래 대화","결제 내역","인수 사진","하자 확인 기록","연락 이력","질문 목록"},
                    {"게시물 원본 주소","작성 시각","공개 범위","화면 캡처","수정 전 내용","관련 대화","신고 접수 기록","질문 목록"},
                    {"현장 사진","발생 시각","사고 경위","관련 대화","지출 영수증","수리 내역","확인한 자료","질문 목록"}
                };
                var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
                int added=0;
                for(int i=0;i<100;i++){
                    int cat=i%5,topic=(i/5)%5;String title=String.format("[더미 확장 %03d] %s",i+1,topics[cat][topic]);
                    try(var s=c.prepareStatement("SELECT COUNT(*) FROM legal_notebooks WHERE username=? AND title=?")){s.setString(1,owner);s.setString(2,title);try(var r=s.executeQuery()){r.next();if(r.getLong(1)>0)continue;}}
                    Long source=null;try(var s=c.prepareStatement("SELECT id FROM legal_cases WHERE category=? AND published=true ORDER BY id LIMIT 1")){s.setString(1,categories[cat]);try(var r=s.executeQuery()){if(r.next())source=r.getLong(1);}}
                    var start=today.minusDays(35+i*2);
                    long id=add(c,"INSERT INTO legal_notebooks(username,title,category,source_case_id,consultation_summary,consultation_questions,consultation_goal,created_at) VALUES(?,?,?,?,?,?,?,?)",owner,title,categories[cat],source,
                        "[가상 테스트 자료] "+topics[cat][topic]+" 관련 사실과 자료를 정리합니다. 확인된 사실, 당사자 주장, 추가 확인 사항을 구분하는 연습용이며 실제 사건이 아닙니다. 사례 번호 "+(i+1),
                        "1. 각 자료의 날짜와 원본은 확인했나요?\n2. 당사자 주장과 확인된 사실이 다른 부분은 무엇인가요?\n3. 추가로 준비할 자료는 무엇인가요?",
                        new String[]{"자료 수집 및 날짜 정리","상담 질문 구체화","누락 자료 보완","후속 일정 확인"}[i%4],start.atTime(9+i%9,i%60));
                    String[] kinds={"계약","송금","통지","상담","기타"};
                    String[] actions={"최초 상황 정리","약정 내용 확인","입금 자료 보관","관련 대화 정리","상대방에게 연락","통지 수신 확인","추가 자료 수집","상담 질문 작성","상담 메모 정리","다음 일정 점검"};
                    for(int j=0;j<10;j++)add(c,"INSERT INTO legal_timeline(notebook_id,occurred_on,kind,title,note) VALUES(?,?,?,?,?)",id,start.plusDays(j*3),kinds[j%5],"[가상] "+actions[j],"사례 "+(i+1)+" / "+topics[cat][topic]+" / "+actions[j]+" 단계입니다. 원본 자료와 날짜를 구분해 기록합니다. 실제 법률 판단이나 실제 거래 기록이 아닙니다.");
                    for(int j=0;j<8;j++)add(c,"INSERT INTO legal_checklist(notebook_id,label,completed) VALUES(?,?,?)",id,labels[cat][j],j<(i%9));
                    for(int j=0;j<3;j++)add(c,"INSERT INTO legal_consultation_logs(notebook_id,consulted_on,answer,next_actions,completed) VALUES(?,?,?,?,?)",id,start.plusDays(20+j*5),"[가상 상담 "+(j+1)+"회] "+topics[cat][topic]+" 자료의 원본, 작성 날짜, 누락 여부를 확인하는 학습용 메모입니다. 법률 전문가의 실제 답변이 아니며 법적 결론을 제시하지 않습니다.",new String[]{"자료를 날짜순으로 정리하고 누락 목록 만들기","확인되지 않은 주장을 표시하고 질문 보완하기","보완 자료 및 다음 확인 일정 정리하기"}[j],j<(i%4));
                    added++;
                }
                c.commit();System.out.println("Committed additional notebooks="+added+", timeline="+(added*10)+", checklist="+(added*8)+", consultation logs="+(added*3));
                for(String table:List.of("legal_notebooks","legal_timeline","legal_checklist","legal_consultation_logs")){String sql=table.equals("legal_notebooks")?"SELECT COUNT(*) FROM legal_notebooks WHERE username=?":"SELECT COUNT(*) FROM "+table+" x JOIN legal_notebooks n ON n.id=x.notebook_id WHERE n.username=?";try(var s=c.prepareStatement(sql)){s.setString(1,owner);try(var r=s.executeQuery()){r.next();System.out.println("VERIFIED "+table+"="+r.getLong(1));}}}
                try(var s=c.prepareStatement("SELECT category,COUNT(*) FROM legal_notebooks WHERE username=? GROUP BY category")){s.setString(1,owner);try(var r=s.executeQuery()){while(r.next())System.out.println(r.getString(1)+"="+r.getLong(2));}}
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){System.out.println("Database operation failed: SQLState="+e.getSQLState()+", code="+e.getErrorCode());System.exit(2);}
    }
    static long add(Connection c,String sql,Object... values)throws SQLException {
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){return r.next()?r.getLong(1):0;}}
    }
}
