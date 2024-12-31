//cczg 2024
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

public class DichotomyBackups {
    private static final String CONFIG_FILE_NAME="backups_dichotomy.properties";
    private static final String VALUE1_NAME ="minutes_between_each_delete";
    private static final String VALUE2_NAME ="delete_mode";

    private static final double[] DECAY_RATIO ={0.75,2/3.0,0.5,1/3.0};
    private static int millisecondGap;
    private static int deleteMode;
    private static void createDefaultConfig() throws IOException {
        String note="minutes between each action should be an integer\n" +
                "there are 8 delete modes, (default 1):\n" +
                "mode 0: each time, only keep n first file with decay ratio 3/4 (wrt (3/4)^n)\n" +
                "mode 1: each time, only keep n first file with decay ratio 2/3 (wrt (2/3)^n)\n" +
                "mode 2: each time, only keep n first file with decay ratio 1/2 (wrt (1/2)^n)\n" +
                "mode 3: each time, only keep n first file with decay ratio 1/3 (wrt (2/3)^n)\n" +
                "delete the second and the last in each 3 of backups sorted from the oldest to the newest\n" +
                "mode 4: mode 0 with a instant delete after running\n" +
                "mode 5: mode 1 with a instant delete after running\n" +
                "mode 6: mode 2 with a instant delete after running\n" +
                "mode 7: mode 3 with a instant delete after running\n" +
                "The newest backup will always be ignored.\n\n"+
                "This program sort backups base on created time\n" +
                "supports 7z, rar, zip, tar format backups, all those files in this folder will be seen as backup\n" +
                "please make sure there's no other(not backup) 7z, rar, zip,tar file or they might be deleted.";

        Properties defaultProperties = new Properties();

        defaultProperties.setProperty(VALUE1_NAME, "120");
        defaultProperties.setProperty(VALUE2_NAME, "1");

        try (FileOutputStream outputStream = new FileOutputStream(CONFIG_FILE_NAME)) {
            defaultProperties.store(outputStream, note);
        } catch (IOException e) {
            throw new IOException(e);
        }

        System.out.println("Default "+CONFIG_FILE_NAME+" created.");
    }
    private static void initialize() throws IOException {
        Properties properties = new Properties();
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            System.out.println("Config file not found. Creating default "+CONFIG_FILE_NAME+"...");
            try {
                createDefaultConfig();
            }catch (Exception e){
                System.out.println("fatal error while creating default config file");
                throw new IOException();
            }
        }
        try {
            try (FileInputStream inputStream = new FileInputStream(CONFIG_FILE_NAME)) {
                properties.load(inputStream);
            }catch (Exception e){
                System.out.println("fatal error while reading config");
                throw new IOException();
            }

            millisecondGap = Integer.parseInt(properties.getProperty(VALUE1_NAME))*60*1000;
            deleteMode = Integer.parseInt(properties.getProperty(VALUE2_NAME));

            System.out.println("deleting after each: " + millisecondGap/60/1000/60.0+"hours.");
            System.out.println("delete mode: " + deleteMode);

        } catch (NumberFormatException e) {
            System.err.println("fatal error with config value, please make sure both of them are integers");
            throw new IOException();
        }
    }

    private static void runDelete(){
        File currentDirectory = new File(".");
        File[] files = currentDirectory.listFiles();
        ArrayList<File> backupFiles = new ArrayList<>();
        ArrayList<Long> createTimes = new ArrayList<>();
        System.out.println("Deletion started at "+
                new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss").format(Calendar.getInstance().getTime()));
        if (files != null) {
                for (File file : files) {
                    if(file.getName().contains(".zip")||file.getName().contains(".rar")
                            ||file.getName().contains(".7z")||file.getName().contains(".tar")){
                        try {
                            long createMillis=Files.readAttributes(file.toPath(),
                                    BasicFileAttributes.class).creationTime().toMillis();
                            createTimes.add(createMillis);
                            backupFiles.add(file);
                        }catch (Exception e){
                            System.out.println("error while checking "+file.getName()+", it will be ignored in this deletion");
                        }
                    }
                }
                if(backupFiles.size()==0){
                    System.out.println("no backup found, might be an error, this delete is skipped.");
                    return;
                }
            Map<Long, Integer> valueToIndexMap = new HashMap<>();
            for (int i = 0; i < createTimes.size(); i++) {
                valueToIndexMap.put(createTimes.get(i),i);
            }
            createTimes.sort(Long::compareTo);
            int totalBackup=createTimes.size();
            int totalDelete=0;
            int successDelete=0;
            long latestMills=createTimes.get(createTimes.size()-1);
            double thresholdMillis=(latestMills-createTimes.get(0))* DECAY_RATIO[deleteMode];
            for (int i = 1; i < createTimes.size()-1; i++) {
                long tempDiffMillis = latestMills- createTimes.get(i);
                if(tempDiffMillis<=thresholdMillis){
                    thresholdMillis*= DECAY_RATIO[deleteMode];
                    continue;
                }
                File tempFile=backupFiles.get(valueToIndexMap.get(createTimes.get(i)));
                String tempName=tempFile.getName();
                totalDelete++;
                if(tempFile.delete()){
                    System.out.println(tempName+" is deleted.");
                    successDelete++;
                }else{
                    System.out.println("error, "+tempName+" can't be deleted.");
                }
            }
            System.out.println("Summary: "+totalBackup+" total backups found,"+
                        successDelete+" out of "+totalDelete+" success.");
        } else {
            System.out.println("an error occurred while reading files in this folder.");
            System.out.println("no file found, this delete is skipped.");
        }
    }

    private static void deleteLooping() throws InterruptedException {
        long nextDeleteMillis,waitingMillis;
        if(deleteMode>3){
            deleteMode-=4;
            runDelete();
        }
        while (true) {
            nextDeleteMillis = System.currentTimeMillis() + millisecondGap;
            waitingMillis=nextDeleteMillis-System.currentTimeMillis();
            System.out.println("Next deletion is after "+waitingMillis/(1000*60.0)+"minutes.");
            Thread.sleep(waitingMillis);
            runDelete();
        }
    }


    public static void main(String[] args) {
        try {
            initialize();
        }catch (Exception e){
            System.out.println("Program terminated.");
            return;
        }
        try{
            deleteLooping();
        }catch (Exception e){
            System.out.println("Program thread was interrupted.");
            System.out.println("Program terminated.");
        }
    }
}
