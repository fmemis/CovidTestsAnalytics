import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Thread.sleep;

public class Helper {

    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd");

    Map<String, String> calculateWeeklyStatistics(String jsonFilesPath) throws IOException {
        return calculatePeriodicalStatistics(jsonFilesPath, 7);
    }

    Map<String, String> calculatePeriodicalStatistics(String jsonFilesPath, int numberOfDays) throws IOException {

        Map<String, String> averageMap = new HashMap<String, String>();

        LocalDate date = LocalDate.now();

        //Map<LocalDate, ReportPojo> jsonMap = new HashMap<LocalDate, ReportPojo>();
        ObjectMapper mapper;
        ReportPojo json;

        int totalTests = 0;
        int totalCases = 0;
        int count = 0;
        for (int i = 0; i < numberOfDays; ++i) {
            mapper = new ObjectMapper();
            String jsonFilePath = jsonFilesPath + dateFormat.format(date) + ".json";
            File f = new File(jsonFilePath);
            if (f.exists()) {
                json = mapper.readValue(f, ReportPojo.class);
                totalTests += json.getTotalTests();
                totalCases += json.getCases();
                count++;
                //jsonMap.put(date,json);
            }
            date = date.minusDays(1);
        }

        int averageTests =  totalTests /  count;
        int averageCases = totalCases / count;
        float averagePositivityPercentage = (float) (100 * totalCases) / (float) totalTests;

        averageMap.put("averageTests",String.valueOf(averageTests));
        averageMap.put("averageCases",String.valueOf(averageCases));
        averageMap.put("averagePositivityPercentage",String.valueOf(averagePositivityPercentage));

        return averageMap;

    }

    void createDailyJson(int actualToday, int totalMoriaka, int totalRapid, int totalCases, int deaths, int intubatedPatients, float percentage, String jsonFilePath) throws JsonProcessingException {
        var mapper = new ObjectMapper();
        var json = mapper.createObjectNode();
        json.put("cases", totalCases);
        json.put("totalTests", actualToday);
        json.put("pcrTests", totalMoriaka);
        json.put("rapidTests", totalRapid);
        json.put("positivityPercentage", percentage);
        json.put("deaths",deaths);
        json.put("intubatedPatients", intubatedPatients);
        String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        this.writeJsonFile(jsonString,jsonFilePath);
    }

    void copyPdfFromEodySite(String pdfUrl, String filePath) throws IOException, InterruptedException {
        sleep(2000);
        URL url = new URL(pdfUrl);
        InputStream in = url.openStream();
        Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
        in.close();
    }

     Map<String, String> parseEodyPdf(File f) throws IOException, InterruptedException {
        String parsedText;
        PDFParser parser = new PDFParser(new RandomAccessFile(f, "r"));
        parser.parse();

        COSDocument cosDoc = parser.getDocument();
        PDFTextStripper pdfStripper = new PDFTextStripper();
        PDDocument pdDoc = new PDDocument(cosDoc);
        parsedText = pdfStripper.getText(pdDoc);
        pdDoc.close();

        //parsedText = parsedText.replaceAll("[\\r\\n]+\\s", "");
        parsedText = parsedText.replaceAll(System.getProperty("line.separator"), " ");
        Map<String,String> data = new HashMap<String, String>();

        data.put("cases","Τα νέα εργαστηριακά επιβεβαιωμένα κρούσματα της νόσου είναι\\s+([0-9]{1,5})");
        data.put("deaths","Οι νέοι θάνατοι ασθενών με COVID-19 είναι\\s+([0-9]{1,5})");
        data.put("pcrTests","έχουν συνολικά ελεγχθεί\\s+([0-9]{1,8})");
        data.put("rapidTests","Rapid Ag έχουν ελεγχθεί\\s+([0-9]{1,8})");
        data.put("intubatedPatients","διασωληνωμένοι είναι\\s+([0-9]{1,8})");

        for ( String key : data.keySet()) {
            data.put(key,findValueInText(data.get(key), parsedText));
        }

        return data;
    }

     String findValueInText(String pattern, String parsedText) {
        Pattern p;
        Matcher m;
        p = Pattern.compile(pattern);
        m = p.matcher(parsedText);
        return m.find() ? m.group(1) : "0";
    }



     boolean writeJsonFile(String json,String jsonFilePath) {
        FileWriter file = null;
        boolean success = true;
        try {
            // Constructs a FileWriter given a file name, using the platform's default charset
            file = new FileWriter(jsonFilePath);
            file.write(json);

        } catch (IOException e) {
            e.printStackTrace();
            success = false;

        } finally {

            try {
                file.flush();
                file.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            finally {
                return success;
            }
        }
    }
}
