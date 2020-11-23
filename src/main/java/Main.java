import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

import static java.lang.Thread.sleep;

public class Main {

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String reportTimeString = " 18:15";
    private static final String saveDirectory = "/home/fotis/EodyReports/";
    private static final String startingExistingPdfReport = "/home/fotis/EodyReports/20201122.pdf";
    private static final String weeklyAverageDay = "FRIDAY";

    public static void main(String[] args) throws IOException, InterruptedException {
        long diff;
        String previousFilePath = startingExistingPdfReport;
        Helper helperClass = new Helper();
        Map<String, String> weeklyAverages;
        while(true) {

            diff = 0;
            LocalDate currentDate = LocalDate.now();
            String currentFilePath = saveDirectory + dateFormat.format(currentDate) + ".pdf";
            String reportDateTimeString;
            LocalDateTime reportTime;
            if (currentFilePath.equals(previousFilePath)) {
                reportDateTimeString = currentDate.plusDays(1).toString() + reportTimeString;
                reportTime = LocalDateTime.parse(reportDateTimeString, dateTimeFormat);
            }
            else {
                reportDateTimeString = currentDate.toString() + reportTimeString;
                reportTime = LocalDateTime.parse(reportDateTimeString, dateTimeFormat);
            }

            String pdfUrl = "https://eody.gov.gr/wp-content/uploads/" +  currentDate.getYear() + "/"
                    + currentDate.getMonthValue() + "/covid-gr-daily-report-" + dateFormat.format(currentDate) + ".pdf";
            URL url = new URL(pdfUrl);

            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(reportTime))
                diff = now.until(reportTime, ChronoUnit.MILLIS);
            else  {
                /*System.out.println(dateFormat.format(currentDate));*/
                HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                int responseCode = huc.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    diff = TimeUnit.MINUTES.toMillis(3);
                }
            }
            if (diff > 0) {
                System.out.println("You shall wake after " + TimeUnit.MILLISECONDS.toMinutes(diff) + " minutes");
                sleep(diff);
                continue;
            }
            //InputStream in = new URL("https://eody.gov.gr/wp-content/uploads/2020/11/covid-gr-daily-report-20201102.pdf").openStream();
            System.out.println("Tα τεστς για σημερα "  + currentDate + ":");
            helperClass.copyPdfFromEodySite(pdfUrl, currentFilePath);

            File f = new File(currentFilePath);
            Map<String, String> todaysNumbers = helperClass.parseEodyPdf(f);

            f = new File(previousFilePath);
            //if for some reason yesterday's pdf is deleted from file system.
            if (!f.exists()) {
                pdfUrl = "https://eody.gov.gr/wp-content/uploads/" +  currentDate.minusDays(1).getYear() + "/"
                        + currentDate.minusDays(1).getMonthValue() + "/covid-gr-daily-report-" + dateFormat.format(currentDate.minusDays(1)) + ".pdf";
                helperClass.copyPdfFromEodySite(pdfUrl, previousFilePath);
                f = new File(previousFilePath);
            }
            Map<String, String> yesterdaysNumbers = helperClass.parseEodyPdf(f);


            int totalToday = Integer.parseInt(todaysNumbers.get("pcrTests")) + Integer.parseInt(todaysNumbers.get("rapidTests"));
            int totalYesterday = Integer.parseInt(yesterdaysNumbers.get("pcrTests")) + Integer.parseInt(yesterdaysNumbers.get("rapidTests"));
            int actualToday = totalToday - totalYesterday;
            int totalMoriaka = Integer.parseInt(todaysNumbers.get("pcrTests")) - Integer.parseInt(yesterdaysNumbers.get("pcrTests"));
            int totalRapid = Integer.parseInt(todaysNumbers.get("rapidTests")) - Integer.parseInt(yesterdaysNumbers.get("rapidTests"));
            int totalCases = Integer.parseInt(todaysNumbers.get("cases"));
            int deaths = Integer.parseInt(todaysNumbers.get("deaths"));
            int intubatedPatients = Integer.parseInt(todaysNumbers.get("intubatedPatients"));

            float percentage = (float) (100 * totalCases) / actualToday;

            System.out.println("\nΈγιναν " + actualToday + " tests σήμερα");
            System.out.println(totalMoriaka + " μοριακά");
            System.out.println(totalRapid + " rapid");
            System.out.println(totalCases + " κρούσματα");
            System.out.println(deaths + " θανατοι");
            System.out.println(intubatedPatients + " διασωληνωμενοι\n");
            System.out.println("Το ποσοστό θετικότητας στα τεστ είναι " + percentage + "%\n");

            String jsonFilePath = saveDirectory + dateFormat.format(currentDate) + ".json";
            helperClass.createDailyJson(actualToday, totalMoriaka, totalRapid, totalCases, deaths, intubatedPatients, percentage, jsonFilePath);

            String weeklyJsonFilePath = saveDirectory + dateFormat.format(currentDate) + "-weekly.json";
            if (currentDate.getDayOfWeek().toString().equals(weeklyAverageDay)) {
                var mapper = new ObjectMapper();
                var json = mapper.createObjectNode();
                weeklyAverages = helperClass.calculateWeeklyStatistics(saveDirectory);
                System.out.println("Έγιναν " + weeklyAverages.get("averageTests") + " tests κατά μέσο όρο την προηγούμενη βδομαδα");
                System.out.println("Yπήρχαν " + weeklyAverages.get("averageTests") + " κρούσματα κατά μέσο όρο την προηγούμενη βδομαδα");
                System.out.println("Το ποσοστο θετικότητας της βδομάδας ήταν " + weeklyAverages.get("averagePositivityPercentage"));
                mapper = new ObjectMapper();
                json = mapper.createObjectNode();
                json.put("averageTests",weeklyAverages.get("averageTests"));
                json.put("averageCases",weeklyAverages.get("averageCases"));
                json.put("averagePositivityPercentage",weeklyAverages.get("averagePositivityPercentage"));
                String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                helperClass.writeJsonFile(jsonString,weeklyJsonFilePath);
            }

            previousFilePath = currentFilePath;

        }
    }
}
