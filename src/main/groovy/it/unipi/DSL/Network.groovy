package it.unipi.DSL

import parser.*
import groovy.json.JsonSlurper
import java.net.*
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.StandardOpenOption
import java.nio.file.Paths
import java.nio.file.Files
import java.time.*
import java.time.format.DateTimeFormatter

// caida network submodule

/*
    Main method for the HTTP request to the CAIDA server.
    It identifies all the 'cycle' meant to be included within the timeframe
*/
class Network {
    def dataHttpRequest(String start, String end, String country, boolean verbose, int meas_limit) {
        country = country.toLowerCase()
        String surl = "https://publicdata.caida.org/datasets/topology/ark/ipv4/probe-data/team-1/"
        String filename

        // Parse input date strings
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDate startDate = LocalDate.parse(start, inputFormatter);
        LocalDate endDate = LocalDate.parse(end, inputFormatter);

        // Check if start date is after end date
        if (startDate.isAfter(endDate)) {
            println("Error: start date is after end date")
            return -1
        }

        // Format output dates
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String startDateStr = startDate.format(outputFormatter);
        String endDateStr = endDate.format(outputFormatter);

        if (verbose)  {
            println("Start date: " + startDateStr)
            println("End date: " + endDateStr)
        }

        // obtaining the correct date
        String year = startDateStr.substring(0, 4)
        surl += year + '/'
        if (verbose) println(surl) // verbose

        def url = new URL(surl)
        def conn = url.openConnection()

        def responseCode = conn.getResponseCode()
        if (responseCode == 200) {
            InputStream is = conn.getInputStream()
            BufferedReader br = new BufferedReader(new InputStreamReader(is))
            String line
            while ((line = br.readLine()) != null) {
                // devo prendere tutti i cicli compresi fra data d'inizio e data di fine
                if (line.contains("cycle") && meas_limit > 0) {
                    String dateStr = line.substring(line.indexOf("cycle") + "cycle".length() + 1, line.indexOf("/", line.indexOf("cycle") + "cycle".length() + 1))
                    LocalDate cycleDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    if ((cycleDate.isAfter(startDate) && cycleDate.isBefore(endDate)) || cycleDate.isEqual(startDate) || cycleDate.isEqual(endDate)) {
                        String cycle = line.substring(line.indexOf("cycle"), line.indexOf("/", line.indexOf("cycle") + "cycle".length() + 1))
                        download(surl, cycle, country, verbose, meas_limit)
                    }
                }
            }
            br.close()
        } else {
            println("Error during the HTTP request to the server: code ${responseCode}")
            return -1
        }

        return 1
    }

    /*
        Method which, given the cycle, will download all the warts data inside the cycle,
        accordingly to the source country.
    */

    def download(String path, String cycle, String country, boolean verbose, int meas_limit) {
        println ("chiamato download")
        String surl = path + cycle + '/'
        if (verbose) println(surl)

        def url = new URL(surl)
        def conn = url.openConnection()

        def responseCode = conn.getResponseCode()
        if (responseCode == 200) {
            InputStream is = conn.getInputStream()
            BufferedReader br = new BufferedReader(new InputStreamReader(is))
            String line

            while ((line = br.readLine()) != null && meas_limit > 0) {
                if (line.contains('warts') && line.contains(country)) {
                    // obtaining filename
                    if (verbose) println("found")
                    String filename = line.substring(line.indexOf("href=") + "href=".length() + 1, line.indexOf("\"", line.indexOf("href=") + "href=".length() + 1))
                    getFile(surl, filename, verbose)
                    meas_limit--
                }       
            }
            br.close()
        } else {
            println("Error during the HTTP request to the server: code ${responseCode}")
            return -1
        }

        return 1
    }

    /*
        Method which, given the filename, will download the warts data, decompress it and dump it in a JSON file.
    */

    def getFile(String path, String filename, boolean verbose) {
        if (verbose) println("Downloading file: " + filename)
        path += filename
        def url = new URL(path)
        def conn = url.openConnection()

        InputStream is = conn.getInputStream()
        BufferedReader br = new BufferedReader(new InputStreamReader(is))

        def responseCode = conn.getResponseCode()
        if (responseCode == 200) {
            File dir = new File("raw_data")
            if (!dir.exists()) {
                dir.mkdir()
            }

            // downloading the file
            File file = new File(dir, filename)
            FileOutputStream fos = new FileOutputStream(file)
            byte[] buffer = new byte[4096]
            int bytesRead
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead)
            }
            fos.close()
            is.close()

            // decompressing the file
            Process p = "gzip -d raw_data/${filename}".execute()
            p.waitFor()
            if (verbose) println("decompressed")

            // dumping the file in a JSON format
            filename -= ".gz"
            dir = new File("IPv4")
            if (!dir.exists()) {
                dir.mkdir()
                if (verbose) println("created dir")
            }
            def jsonFile = new File(dir, "dataset.json")
            if (!jsonFile.exists()) {
                jsonFile.createNewFile()
                if (verbose) println("created file")
            }
            println filename
            warts2json("raw_data/${filename}", "IPv4/dataset.json")
            if (verbose) println("dumped")
            br.close()
        } else {
            println("Error during the HTTP request to the server: code ${responseCode}")
            return -1
        }

        return 1
    }


    /*
        Function which creates/enriches the dataset.
        
        Through multiple libraries and tools, the WARTS file is converted into a record set and 
        then written in append mode to the JSON file.
        The dataset generated per query may be quite large, therefore the append mode is used to avoid memory issues.
    */
    def warts2json(String warts, String json) {
        def cmd = "sc_warts2json ${warts}"
        def process = cmd.execute()
        def jsonFile = new File(json)
        try {
            def inputStream = new BufferedInputStream(process.inputStream)
            def outputStream = new FileOutputStream(jsonFile, true)  // 'true' for append mode

            byte[] buffer = new byte[8192]
            int bytesRead

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            inputStream.close()
            outputStream.close()

            process.waitFor()

            return process.exitValue()
        } catch (Exception e) {
            e.printStackTrace()
            return -1
        }
    }
}
