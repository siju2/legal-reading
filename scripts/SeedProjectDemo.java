import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

class SeedProjectDemo {
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
                try(var s=c.prepareStatement("SELECT COUNT(*) FROM members WHERE username=?")){s.setString(1,owner);try(var r=s.executeQuery()){r.next();if(r.getLong(1)>0){System.out.println("Demo account already exists; no changes made.");c.rollback();return;}}}
                String pass="Demo!"+UUID.randomUUID().toString().replace("-", "").substring(0,16);
                String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(pass);
                add(c,"INSERT INTO members(username,password,admin) VALUES(?,?,false)",owner,hash);
                String[][] samples={
                    {"주거","보증금 반환 준비","임대차 종료 통지와 보증금 정산 내역을 정리하는 가상 사례입니다.","종료 통지 수신 여부와 인도 일정을 확인하려면 어떤 자료가 필요한가요?","계약서·종료 통지·정산 자료 준비","임대차계약서","종료 통지 수신 기록","보증금 입금 내역","인도 및 정산 기록"},
                    {"일자리","상여금 및 급여 자료 정리","상여금 지급 조건과 급여명세를 비교하는 가상 사례입니다.","지급 규정과 대상 기간을 어떤 순서로 비교하면 좋을까요?","근로 기간과 지급 조건 정리","근로계약서","취업규칙 및 보수규정","급여명세서","출퇴근 기록"},
                    {"돈·거래","중고 노트북 거래 기록","판매 설명과 수령 당시 상태를 비교하는 가상 사례입니다.","거래 전 설명과 하자 확인 시점을 어떻게 기록할까요?","거래 자료와 확인 시점 정리","판매 게시글","판매자와의 대화","송금 내역","수령 당시 사진"},
                    {"온라인 생활","게시물 증거 보관","온라인 게시물의 원본 주소와 공개 범위를 정리하는 가상 사례입니다.","게시물 수정 전후 내용과 공개 범위는 어떻게 보관하나요?","원본과 변경 기록 보관","원본 게시물 주소","작성 시각 기록","공개 범위 화면","수정 전후 화면"},
                    {"일상 사고","생활 사고 경위 정리","이동 중 사고의 시간과 장소 및 지출 자료를 정리하는 가상 사례입니다.","사고 경위와 지출 내역을 구분해 어떤 자료를 준비하나요?","시간순 경위 및 증빙 정리","현장 사진","사고 시각 메모","관련 대화 기록","지출 영수증"}
                };
                java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
                for(String[] a:samples){
                    Long source=null;try(var s=c.prepareStatement("SELECT id FROM legal_cases WHERE category=? AND published=true ORDER BY id LIMIT 1")){s.setString(1,a[0]);try(var r=s.executeQuery()){if(r.next())source=r.getLong(1);}}
                    long id=add(c,"INSERT INTO legal_notebooks(username,title,category,source_case_id,consultation_summary,consultation_questions,consultation_goal,created_at) VALUES(?,?,?,?,?,?,?,?)",owner,"[더미] "+a[1],a[0],source,"[테스트 데이터] "+a[2],a[3],a[4],java.time.LocalDateTime.now());
                    for(int j=0;j<3;j++)add(c,"INSERT INTO legal_timeline(notebook_id,occurred_on,kind,title,note) VALUES(?,?,?,?,?)",id,today.minusDays(14-j*5),new String[]{"계약","통지","상담"}[j],new String[]{"[더미] 최초 상황 기록","[더미] 자료 확인 및 연락","[더미] 상담 준비 검토"}[j],"가상 학습 데이터입니다. 실제 사건이나 상담 결과가 아닙니다. "+a[2]);
                    for(int j=0;j<4;j++)add(c,"INSERT INTO legal_checklist(notebook_id,label,completed) VALUES(?,?,?)",id,a[5+j],j<2);
                    add(c,"INSERT INTO legal_consultation_logs(notebook_id,consulted_on,answer,next_actions,completed) VALUES(?,?,?,?,?)",id,today.minusDays(1),"[가상 상담 메모] 확인된 사실과 추측을 구분하고 원본 자료의 날짜를 정리하는 연습입니다. 실제 법률 상담이나 법적 판단이 아닙니다.",a[4]+" 후 누락된 날짜 확인",false);
                    if(source!=null)add(c,"INSERT INTO legal_bookmarks(username,case_id,saved_at,note) VALUES(?,?,?,?)",owner,source,java.time.LocalDateTime.now(),"[더미] "+a[1]+" 참고 자료. 실제 사건에 그대로 적용하지 않습니다.");
                }
                for(int i=1;i<=3;i++)add(c,"INSERT INTO legal_ai_usage(username,usage_date,attempts) VALUES(?,?,?)",owner,today.minusDays(i),i);
                for(String state:List.of("READY","CANCELLED"))add(c,"INSERT INTO legal_payment_orders(payment_id,username,store_id,amount,pass_days,status,created_at,version) VALUES(?,?,?,?,?,?,?,?)","DEMO_ONLY_"+state+"_20261104",owner,"DEMO_NOT_A_REAL_STORE",9900,30,state,java.time.LocalDateTime.now(),0);
                // Verify related rows and password encoding before committing.
                if(!new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(pass,hash))throw new IllegalStateException("Password verification failed");
                for(String table:List.of("legal_notebooks","legal_ai_usage","legal_payment_orders","legal_bookmarks")){try(var s=c.prepareStatement("SELECT COUNT(*) FROM "+table+" WHERE username=?")){s.setString(1,owner);try(var r=s.executeQuery()){r.next();System.out.println(table+" demo rows="+r.getLong(1));}}}
                for(String table:List.of("legal_timeline","legal_checklist","legal_consultation_logs")){try(var s=c.prepareStatement("SELECT COUNT(*) FROM "+table+" x JOIN legal_notebooks n ON x.notebook_id=n.id WHERE n.username=?")){s.setString(1,owner);try(var r=s.executeQuery()){r.next();System.out.println(table+" demo rows="+r.getLong(1));}}}
                Path credentials=Path.of(args[1]); Files.createDirectories(credentials.toAbsolutePath().getParent()); if(Files.exists(credentials))throw new IllegalStateException("Credentials file already exists; refusing to overwrite.");
                c.commit();
                Files.writeString(Path.of(args[1]),"테스트 계정: "+owner+"\n비밀번호: "+pass+"\n관리자 권한 없음. 결제 샘플은 READY/CANCELLED 상태이며 실제 결제 및 유료 권한 없음.\n모든 노트와 상담기록은 가상 자료입니다.\n",StandardCharsets.UTF_8);
                System.out.println("COMMITTED; credentials saved locally (not printed).");
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){System.out.println("Database operation failed: SQLState="+e.getSQLState()+", code="+e.getErrorCode());System.exit(2);}
    }
    static long add(Connection c,String sql,Object... values)throws SQLException {
        try(var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();try(var r=s.getGeneratedKeys()){return r.next()?r.getLong(1):0;}}
    }
}
