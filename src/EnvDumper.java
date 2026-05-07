import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

public class EnvDumper {
    public static void main(String[] args) throws Exception {
        PrintWriter file = new PrintWriter(new FileWriter("env_dump.txt"));
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            file.println(entry.getKey() + "=" + entry.getValue());
        }
        file.close();
    }
}
