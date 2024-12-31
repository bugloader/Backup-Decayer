import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

public class generator {
    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            Thread.sleep(1000);
            try (PrintWriter writer = new PrintWriter(i+".zip")) {
                writer.println(i);
                writer.println("This is another line.");
                System.out.println("Content written to file.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
