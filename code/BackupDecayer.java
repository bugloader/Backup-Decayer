//cczg 2024
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

public class BackupDecayer {

    private static final SimpleDateFormat DateFormat= new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
    private static final String CONFIG_FILE_NAME="Backup_Decayer.properties";
    private static final String VALUE1_NAME ="minutes_between_each_search";
    private static final String VALUE2_NAME ="delete_mode";
    private static final String VALUE3_NAME ="maximum_keep";
    private static final String VALUE4_NAME ="minimum_keep";
    private static final String VALUE5_NAME ="display_mode";
    private static final String VALUE6_NAME ="log_mode";

    private static final String VALUE7_NAME ="maximum_gbs";

    private static final double[] DECAY_RATIO ={0.75,2/3.0,0.5,1/3.0};
    private static int millisecondGap;
    private static int deleteMode;
    private static int maximum_keep;
    private static int minimum_keep;
    private static int displayMode=1;
    private static int logMode=1;
    private static int maximum_gbs;
    private static void createDefaultConfig() throws IOException {
        String note="Every value need to be an integer\n" +
                "There are 4 delete modes, (default 1):\n" +
                "mode 0: each time, only keep n first file with decay ratio 3/4 (wrt (3/4)^n)\n" +
                "mode 1: each time, only keep n first file with decay ratio 2/3 (wrt (2/3)^n)\n" +
                "mode 2: each time, only keep n first file with decay ratio 1/2 (wrt (1/2)^n)\n" +
                "mode 3: each time, only keep n first file with decay ratio 1/3 (wrt (2/3)^n)\n" +
                "Bigger decay ratio provides more backup files.\n\n" +
                "Nothing will be removed until the minimum count is reached.\n" +
                "When either max count or gbs exceed, the oldest backup will be removed.\n"+
                "The newest backup will always be ignored.\n\n"+
                "There are 3 display modes, (default 1):\n" +
                "mode 0: no print output except error\n" +
                "mode 1: brief summary of each deletion + error report\n" +
                "mode 2: detail about each search and deletion\n\n" +
                "There are 3 log modes, (default 1),\n" +
                "They are exactly the same as display mode, except it create .log file instead of printing\n\n" +
                "This program sort backups base on created time\n" +
                "supports 7z, rar, zip, tar format backups, all those files in this folder will be seen as backup\n" +
                "please make sure there's no other(not backup) 7z, rar, zip,tar file or they might be deleted.";

        Properties defaultProperties = new Properties();

        defaultProperties.setProperty(VALUE1_NAME, "120");
        defaultProperties.setProperty(VALUE2_NAME, "1");
        defaultProperties.setProperty(VALUE3_NAME, "20");
        defaultProperties.setProperty(VALUE4_NAME, "2");
        defaultProperties.setProperty(VALUE5_NAME, "1");
        defaultProperties.setProperty(VALUE6_NAME, "1");
        defaultProperties.setProperty(VALUE7_NAME, "40");

        try (FileOutputStream outputStream = new FileOutputStream(CONFIG_FILE_NAME)) {
            defaultProperties.store(outputStream, note);
        } catch (IOException e) {
            throw new IOException(e);
        }

        System.out.println("Default "+CONFIG_FILE_NAME+" created.");
    }

    private static String timeString(){
        return DateFormat.format(Calendar.getInstance().getTime());
    }
    private static String logString="";

    private static void displayAndLog(String message,int priority){
        //priority: 2 details, 1 summary, 0 error
        if(priority<=displayMode){
            if(priority==0){
                System.err.print(message);
            }else {
                System.out.print(message);
            }
        }
        if(priority<=logMode){
            logString+=message;
        }
    }
    private static void displayAndLogln(String message, int priority){
        displayAndLog(message+"\n",priority);
    }

    private static void updateLog(){
        if(logString.equals("")){
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter("BackupDecayer.log",true))) {
            writer.append("update at: ").append(timeString()).append("\n").append(logString).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        logString="";
    }

    private static void initialize() throws IOException {
        Properties properties = new Properties();
        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            displayAndLogln("Config file not found. Creating default "+CONFIG_FILE_NAME+"...",0);
            try {
                createDefaultConfig();
                displayAndLogln("please read and edit the "+CONFIG_FILE_NAME+" and restart this program.",0);
            }catch (Exception e){
                displayAndLogln("fatal error while creating a default config file, \n" +
                        "might be resolved by checking the folder space or the access permission.",0);
            }
            updateLog();
            throw new IOException();
        }
        try {
            try (FileInputStream inputStream = new FileInputStream(CONFIG_FILE_NAME)) {
                properties.load(inputStream);
            }catch (Exception e){
                displayAndLogln("fatal error while reading config",0);
                updateLog();
                throw new IOException();
            }

            millisecondGap = Integer.parseInt(properties.getProperty(VALUE1_NAME))*60*1000;
            deleteMode = Integer.parseInt(properties.getProperty(VALUE2_NAME));
            maximum_keep = Integer.parseInt(properties.getProperty(VALUE3_NAME));
            minimum_keep = Integer.parseInt(properties.getProperty(VALUE4_NAME));
            displayMode = Integer.parseInt(properties.getProperty(VALUE5_NAME));
            logMode = Integer.parseInt(properties.getProperty(VALUE6_NAME));
            maximum_gbs = Integer.parseInt(properties.getProperty(VALUE7_NAME));

            boolean wrongConfig=false;

            if(displayMode<0||displayMode>=3||logMode<0||logMode>=3){
                displayMode=1;
                logMode=1;
                displayAndLogln("fatal error, display_mode and log_mode must be one of 0,1,2",0);
                wrongConfig=true;
            }

            displayAndLogln("deleting_after_each: " + millisecondGap/60/1000/60.0+"hours.",1);
            displayAndLogln("delete_mode: " + deleteMode,1);
            displayAndLogln("maximum_keep: " + maximum_keep,1);
            displayAndLogln("minimum_keep: " + minimum_keep,1);
            displayAndLogln("display_mode: " + displayMode,1);
            displayAndLogln("log_mode: " + logMode,1);
            displayAndLogln("maximum_gbs: " + maximum_gbs,1);

            if (millisecondGap<=0){
                displayAndLogln("fatal error, deleting_after_each must >0",0);
                wrongConfig=true;
            }
            if(deleteMode<0){
                displayAndLogln("fatal error, delete_mode must be one of 0,1,2,3",0);
                wrongConfig=true;
            }
            if(maximum_keep<=0){
                displayAndLogln("fatal error, maximum_keep must >0",0);
                wrongConfig=true;
            }
            if(minimum_keep<=0){
                displayAndLogln("fatal error, minimum_keep must >0",0);
                wrongConfig=true;
            }
            if(maximum_gbs<=0){
                displayAndLogln("fatal error, maximum_gbs must >0",0);
                wrongConfig=true;
            }
            if (wrongConfig){
                throw new InputMismatchException();
            }
        } catch (NumberFormatException e) {
            displayAndLogln("fatal error in config values, please make sure all of them are integers.",0);
            updateLog();
            throw new IOException();
        } catch (InputMismatchException e){
            displayAndLogln("fatal error in config values, please make sure all of them are valid.",0);
            updateLog();
            throw new IOException();
        }
        updateLog();
    }

    private static ArrayList<File> getBackupFiles(File[] files){
        ArrayList<File> backupFiles = new ArrayList<>();
        for (File file : files) {
            if(file.getName().contains(".zip")||file.getName().contains(".rar")
                    ||file.getName().contains(".7z")||file.getName().contains(".tar")){
                backupFiles.add(file);
            }
        }
        return backupFiles;
    }
    private static ArrayList<Long> getBackupCreateMillis(ArrayList<File> backupFiles){
        ArrayList<Long> createTimes = new ArrayList<>();
        for (File backupFile : backupFiles) {
            try {
                long createMillis = Files.readAttributes(backupFile.toPath(),
                        BasicFileAttributes.class).creationTime().toMillis();
                createTimes.add(createMillis);
            } catch (Exception e) {
                displayAndLogln("error while checking " + backupFile.getName() +
                        ", it will be ignored in this deletion.",0);
                backupFiles.remove(backupFile);
            }
        }
        return createTimes;
    }

    public static void runDelete(ArrayList<File> backupFiles,ArrayList<Long> createTimes){
        //check minimum_keep
        if(backupFiles.size()<=minimum_keep){
            displayAndLogln("Minimum not exceed, skip this deletion",2);
            return;
        }

        //regular decay deletion
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
        ArrayList<Long> millisAfterNormalMaintain = new ArrayList<>();
        for (int i = 1; i < createTimes.size()-1; i++) {
            long tempMillis = createTimes.get(i);
            long tempDiffMillis = latestMills - tempMillis;
            File tempFile=backupFiles.get(valueToIndexMap.get(tempMillis));
            if(tempDiffMillis<=thresholdMillis){
                millisAfterNormalMaintain.add(tempMillis);
                thresholdMillis*= DECAY_RATIO[deleteMode];
                continue;
            }
            String tempName=tempFile.getName();
            totalDelete++;
            if(tempFile.delete()){
                displayAndLogln(tempName+" is deleted.",2);
                successDelete++;
            }else{
                displayAndLogln("error, "+tempName+" can't be deleted.",0);
            }
        }
        //delete above maximum_keep
        long restBackupByteSize=0;
        int nonDeletedIndex=Math.min(millisAfterNormalMaintain.size()-maximum_keep,millisAfterNormalMaintain.size()-1);
        if(nonDeletedIndex>0){
            displayAndLogln("Maximum count exceed,",2);
            for (int i = 0; i < nonDeletedIndex; i++) {
                long tempMillis = millisAfterNormalMaintain.get(i);
                File tempFile=backupFiles.get(valueToIndexMap.get(tempMillis));
                String tempName=tempFile.getName();
                totalDelete++;
                if(tempFile.delete()){
                    displayAndLogln(tempName+" is deleted.",2);
                    successDelete++;
                }else{
                    displayAndLogln("error, "+tempName+" can't be deleted.",0);
                }
            }
            //count bytes
            for (int i = nonDeletedIndex; i < millisAfterNormalMaintain.size(); i++) {
                long tempMillis = millisAfterNormalMaintain.get(i);
                File tempFile=backupFiles.get(valueToIndexMap.get(tempMillis));
                restBackupByteSize+=tempFile.length();
            }
        }
        //delete above maximum_gbs
        long maximumBytes= ((long) maximum_gbs) <<30;//kb,mb,gb
        if(restBackupByteSize>maximumBytes){
            displayAndLogln("Maximum gbs exceed,",2);
        }
        for (int i = nonDeletedIndex; i < millisAfterNormalMaintain.size()-1&&restBackupByteSize>maximumBytes; i++) {
            long tempMillis = millisAfterNormalMaintain.get(i);
            File tempFile=backupFiles.get(valueToIndexMap.get(tempMillis));
            String tempName=tempFile.getName();
            totalDelete++;
            if(tempFile.delete()){
                long tempBytes=tempFile.length();
                displayAndLogln(tempName+" is deleted.",2);
                successDelete++;
                restBackupByteSize-=tempBytes;
            }else{
                displayAndLogln("error, "+tempName+" can't be deleted.",0);
            }
        }
        displayAndLogln("Summary: "+totalBackup+" total backups found,"+
                successDelete+" out of "+totalDelete+" attempts deleted successfully.",1);
    }

    private static void runSearchAndDelete(){
        File currentDirectory = new File(".");
        File[] files = currentDirectory.listFiles();
        ArrayList<File> backupFiles;
        ArrayList<Long> createTimes;
        displayAndLogln("Search started at "+timeString(),1);
        if(files==null){
            displayAndLogln("An error occurred while reading files in this folder.",0);
            displayAndLogln("No file found, this delete is skipped.",0);
            updateLog();
            return;
        }

        backupFiles=getBackupFiles(files);
        if(backupFiles.isEmpty()){
            displayAndLogln("No backup found, this might be an error, this delete is skipped.",0);
            updateLog();
            return;
        }
        createTimes=getBackupCreateMillis(backupFiles);
        if(createTimes.size()!= backupFiles.size()){
            displayAndLogln("Error counting backups, can't continue, this deletion will be skipped.",0);
            updateLog();
            return;
        }
        runDelete(backupFiles,createTimes);
        updateLog();
    }

    private static void deleteLooping() throws InterruptedException {
        long nextDeleteMillis,waitingMillis;
        runSearchAndDelete();
        while (true) {
            nextDeleteMillis = System.currentTimeMillis() + millisecondGap;
            waitingMillis=nextDeleteMillis-System.currentTimeMillis();
            //System.out.println("Next deletion is after "+waitingMillis/(1000*60.0)+"minutes.");
            Thread.sleep(waitingMillis);
            runSearchAndDelete();
        }
    }


    public static void main(String[] args) {
        try {
            initialize();
        }catch (Exception e){
            displayAndLogln("Program terminated.",0);
            updateLog();
            return;
        }
        try{
            deleteLooping();
        }catch (Exception e){
            displayAndLogln("Error program thread was interrupted.",0);
            displayAndLogln("Program terminated.",0);
            updateLog();
        }
    }
}
