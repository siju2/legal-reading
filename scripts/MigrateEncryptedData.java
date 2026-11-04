import com.example.project.security.DataCipher;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Offline, allowlisted migration. Stop the app first; keeps an encrypted SQL snapshot. */
public class MigrateEncryptedData {
    static final Map<String,List<String>> FIELDS=new LinkedHashMap<>();
    static {
        FIELDS.put("legal_notebooks",List.of("title","consultation_summary","consultation_questions","consultation_goal"));
        FIELDS.put("legal_timeline",List.of("title","note"));FIELDS.put("legal_checklist",List.of("label"));
        FIELDS.put("legal_consultation_logs",List.of("answer","next_actions"));
        FIELDS.put("legal_bookmarks",List.of("note"));FIELDS.put("legal_revisions",List.of("snapshot"));
        FIELDS.put("legal_cases",List.of("title","tags","summary","case_number","court","outcome","facts","claims","issue","reasoning","comparison","checklist","excerpt","source_url","law_name","law_url","review_status"));
    }
    static String resolve(String v){var m=java.util.regex.Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}").matcher(v);var out=new StringBuffer();while(m.find())m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(Objects.toString(System.getenv(m.group(1)),Objects.toString(m.group(2),""))));m.appendTail(out);return out.toString();}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: PROJECT_ROOT MYSQLDUMP_PATH");
        DataCipher.verifyKey();Properties config=new Properties();
        for(String name:List.of("application.properties","application-local.properties")){Path f=Path.of(args[0],"src/main/resources",name);if(Files.exists(f))try(var r=Files.newBufferedReader(f,StandardCharsets.UTF_8)){config.load(r);}}
        Path encryptedRuntime=com.example.project.security.DataCipher.keyPath().toAbsolutePath().getParent().resolve("runtime.properties.enc");
        if(Files.exists(encryptedRuntime))config.load(new java.io.StringReader(new String(com.example.project.security.DataCipher.decryptFile(Files.readAllBytes(encryptedRuntime)),StandardCharsets.UTF_8)));
        String url=Objects.toString(System.getenv("SPRING_DATASOURCE_URL"),resolve(config.getProperty("spring.datasource.url")));
        if(!url.startsWith("jdbc:mysql://localhost:3306/project?"))throw new IllegalArgumentException("Offline migration is restricted to the configured local project database");
        String user=Objects.toString(System.getenv("SPRING_DATASOURCE_USERNAME"),resolve(config.getProperty("spring.datasource.username","project_app")));
        String password=Objects.toString(System.getenv("SPRING_DATASOURCE_PASSWORD"),resolve(config.getProperty("spring.datasource.password","")));
        Path secretDir=DataCipher.keyPath().toAbsolutePath().getParent();
        Path defaults=Files.createTempFile(secretDir,"dump-", ".cnf");
        Path backup=secretDir.resolve("database-before-encryption-"+System.currentTimeMillis()+".sql.enc");
        try {
            Files.writeString(defaults,"[client]\nhost=localhost\nport=3306\nuser=\""+escape(user)+"\"\npassword=\""+escape(password)+"\"\ndefault-character-set=utf8mb4\n",StandardCharsets.UTF_8);
            Process process=new ProcessBuilder(args[1],"--defaults-extra-file="+defaults,"--single-transaction","--skip-lock-tables","--no-tablespaces","--hex-blob","project").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] sql=process.getInputStream().readAllBytes();
            if(process.waitFor()!=0||sql.length==0)throw new IllegalStateException("SQL snapshot failed; database has not been changed");
            Files.write(backup,DataCipher.encryptFile(sql),StandardOpenOption.CREATE_NEW);
            if(!Arrays.equals(sql,DataCipher.decryptFile(Files.readAllBytes(backup))))throw new IllegalStateException("Encrypted snapshot verification failed");
        }finally{Files.deleteIfExists(defaults);}
        Properties auth=new Properties();auth.setProperty("user",user);auth.setProperty("password",password);
        try(Connection c=DriverManager.getConnection(url,auth)){
            // MySQL DDL commits independently. Widen before the single data transaction.
            for(var entry:FIELDS.entrySet())for(String column:entry.getValue()){
                boolean required;try(var r=c.getMetaData().getColumns(c.getCatalog(),null,entry.getKey(),column)){if(!r.next())throw new IllegalStateException("Column missing");required=r.getInt("NULLABLE")==DatabaseMetaData.columnNoNulls;}
                try(var s=c.createStatement()){s.executeUpdate("ALTER TABLE `"+entry.getKey()+"` MODIFY `"+column+"` LONGTEXT "+(required?"NOT NULL":"NULL"));}
            }
            c.setAutoCommit(false);
            try {
                for(var entry:FIELDS.entrySet()){
                    String table=entry.getKey();var fields=entry.getValue();var rows=new ArrayList<Map<String,Object>>();
                    try(var s=c.createStatement();var r=s.executeQuery("SELECT id,"+String.join(",",fields)+" FROM "+table)){
                        while(r.next()){var row=new LinkedHashMap<String,Object>();row.put("id",r.getObject("id"));for(String col:fields)row.put(col,r.getString(col));rows.add(row);}
                    }
                    String update="UPDATE "+table+" SET "+String.join(",",fields.stream().map(f->f+"=?").toList())+" WHERE id=?";
                    for(var row:rows){try(var s=c.prepareStatement(update)){int i=1;for(String col:fields){String old=(String)row.get(col);String encrypted;
                                if(old!=null&&old.startsWith(DataCipher.PREFIX)){DataCipher.decryptText(old);encrypted=old;}
                                else {encrypted=DataCipher.encryptText(old);if(!Objects.equals(old,DataCipher.decryptText(encrypted)))throw new IllegalStateException("Round-trip failed");}
                                s.setString(i++,encrypted);
                            }s.setObject(i,row.get("id"));s.executeUpdate();}}
                    System.out.println("Encrypted "+table+": "+rows.size()+" rows");
                }
                c.commit();System.out.println("Migration committed. Encrypted SQL recovery snapshot: "+backup.getFileName());
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){System.out.println("Migration failed; SQLState="+e.getSQLState()+", code="+e.getErrorCode());System.exit(2);}
    }
    static String escape(String value){return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
}
