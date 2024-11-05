package it.unipi.DSL

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat
import java.util.Date


// caida parsing submodule


class GeoCacheEntry {
    String address
    List<String> data

    GeoCacheEntry(String address, List<String> data) {
        this.address = address
        this.data = data
    }
}


// class which helds the measurement data from every hop made by the traceroute packet
class Hop {
    String addr
    int probe_ttl
    double rtt
    int reply_ttl
    int icmp_type
    int icmp_code
    int probe_size
    // Geolocalization fields
    String asn = "-1"
    double lat
    double lon

    Hop(String addr, int probe_ttl, double rtt, int reply_ttl, int icmp_type, int icmp_code, int probe_size) {
        this.addr = addr
        this.probe_ttl = probe_ttl
        this.rtt = rtt
        this.reply_ttl = reply_ttl
        this.icmp_type = icmp_type
        this.icmp_code = icmp_code
        this.probe_size = probe_size
    }
}

// class which helds the start time of the traceroute packet
// sec and usec refers (?) to the cycle time
class Start {
    int sec
    int usec
    Date ftime

    Start(int sec, int usec, Date ftime) {
        this.sec = sec
        this.usec = usec
        this.ftime = ftime
    }
}

// transmitter information regarding the hop
class Tx {
    int sec
    int usec
    Tx(int sec, int usec) {
        this.sec = sec
        this.usec = usec
    }
}


// class which helds the measurement data from the traceroute execution as a whole
class Record {
    String type
    String version
    String src
    String dst
    Start start
    List<Hop> hops = []
    int hop_count
    int star_count = 0

    // Geolocalization fields
    String src_country = "N/A"
    String dst_country = "N/A"
    String src_city = "N/A"
    String dst_city = "N/A"
    String src_asn = "-1"
    String dst_asn = "-1"
    Double src_lat = 0.0
    Double src_lon = 0.0
    Double dst_lat = 0.0
    Double dst_lon = 0.0

    Record(String type, String version, String src, String dst, Start start, int hop_count) {
        this.type = type
        this.version = version
        this.src = src
        this.dst = dst
        this.start = start
        this.hop_count = hop_count
    }
    
    // funzione per allineare l'output con il formato definito nel modulo di RIPEAtlas
    Map toMap() {
        return [
            data: start.ftime.format('dd/MM/yyyy'),
            ipsource: src,
            iptarget: dst,
            list_route: hops.groupBy { it.probe_ttl }.collect { ttl, hopList ->
                def hop = hopList[0]
                return [
                    hop: ttl,
                    asn: hop.asn ?: "-1",
                    lon: hop.lon,
                    lat: hop.lat,
                    risposte: hopList.collect { hopInstance -> 
                        return [
                            ipresponse: hopInstance.addr,
                            ttl: hopInstance.reply_ttl,
                            size: hopInstance.probe_size,  
                            rtt: hopInstance.rtt
                        ]
                    }
                ]
            }
        ]
    }
    //
}

/*
    flow of the collector method:
    1. read the file
    2. filter according to hop and star limit
    3. geolocate the source and destination (and coordinates, if requested)
    4. filter according to the target country
    5. write the results to a file
*/

def collector(
    boolean verbose, boolean coord, boolean asn, 
    Integer result_limit, Integer hop_limit, Integer star_limit,
    Integer source_limit, Integer target_limit, 
    String operatorh, String operators, String target_country, String source_country, 
    Integer source_radius, Integer target_radius,
    double[] source_coordinates, double[] target_coordinates, String reqType
    ) {
    ArrayList<Record> records = []
    def result_count = 0
    if (verbose) println ("Collecting records.")
    def geoCache = new ArrayList<GeoCacheEntry>()
    boolean stop = false
    String path = "IPv4/dataset.json"
    def slurper = new JsonSlurper()
    boolean limitReached = false
    try {
        new File(path).withReader { reader ->
            reader.eachLine { line ->
                def record = slurper.parseText(line)
                if (record.type == "trace") {
                    if (stop) throw new Exception()
                    def start = record.start
                    def dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    Date date = dateFormat.parse(start.ftime)
                    def s = new Start(start.sec, start.usec, date)

                    def r = new Record(
                        record.type, record.version, record.src, record.dst, s, record.hop_count
                    )

                    def hops = record.hops
                    if (hops != null && hops.size() != 0) {
                        for (hop in hops) {
                            def h = new Hop(hop.addr, hop.probe_ttl, hop.rtt, hop.reply_ttl, hop.icmp_type, hop.icmp_code, hop.probe_size)
                            r.hops.add(h)
                        }
                    }
                    r.star_count = r.hop_count - r.hops.size()
                    records.add(r)
                    result_count++

                    if (result_count >= result_limit && result_limit != 0)
                        stop = true
                }  
            }
        }
    } catch (Exception e) {
    if (verbose) println ("Limit reached.")
    }

    if (verbose) println ("${records.size()} records collected.")

    int size_orig = records.size()
    // hop limit filtering
    if (hop_limit > 0) {
        if (verbose) println ("Filtering records with $operatorh $hop_limit hops.")
        records.removeIf { !compare(operatorh, it.hop_count, hop_limit)}
        if (verbose) println ("${size_orig-records.size()} records not matching $operatorh $hop_limit hops deleted.\nCurrent records: ${records.size()}\n")
        size_orig = records.size()
    }


    // star limit filtering
    if (star_limit > 0) {
        if (verbose) println ("Filtering records with $operators $star_limit stars.")
        records.removeIf { !compare(operators, it.star_count, star_limit)}
        if (verbose) println ("${size_orig - records.size()} records not matching $operators $star_limit stars deleted.\nCurrent records: ${records.size()}\n")
    }

    // filtering by source/target limit
    limit_filter(records, source_limit, target_limit, verbose)

    // geolocating the source and destination
    if(verbose) println ("Collecting geodata resources.") 
    def geodata = []
    records.each { r ->
        geodata = geoloc(r.src, coord, geoCache)

        // source ips can be part of private IP space, but the source country is bounded to the warts file name
        // so we can safely assume that the source country is the one in the warts filename.
        if (geodata[0] == "N/A") r.src_country = source_country
        else r.src_country = geodata[0]
        r.src_city = geodata[1]
        r.src_lat = geodata[2]
        r.src_lon = geodata[3]

        geodata = geoloc(r.dst, coord, geoCache)
        r.dst_country = geodata[0]
        r.dst_city = geodata[1]
        r.dst_lat = geodata[2]
        r.dst_lon = geodata[3]

    }

    // filtering by target country OR by coordinates
    // the priority of coordinates filtering is higher than the target country filtering
    if (source_coordinates && target_coordinates && source_radius && target_radius) {
        if (source_coordinates.size() != 0 && source_radius != 0) {
            if (verbose) println ("Filtering sources by coordinates and radius: ${source_coordinates[0]}, ${source_coordinates[1]}, ${source_radius}\n")
            records.removeIf { !isWithinRadius(source_coordinates[0], source_coordinates[1], it.src_lat, it.src_lon, source_radius) }
        }

        if (target_coordinates.size() != 0 && target_radius != 0) {
            if (verbose) println ("Filtering targets by coordinates and radius: ${target_coordinates[0]}, ${target_coordinates[1]}, ${target_radius}\n")
            records.removeIf { !isWithinRadius(target_coordinates[0], target_coordinates[1], it.dst_lat, it.dst_lon, target_radius) }
        }

    } else {
        // the source country is bounded to the warts file so no need to filter by source country
        if (verbose) println ("Filtering by target country")
        records.removeIf { r -> r.dst_country != target_country }
    }

    // ASN data collection
    if (asn) {
        if (verbose) println ("Collecting ASN data.")
        records.each { r ->
            r.src_asn = getASN(r.src)
            r.dst_asn = getASN(r.dst)   
            for(hop in r.hops) {
                geodata = geoloc(hop.addr, coord, geoCache)
                hop.lat = geodata[2]
                hop.lon = geodata[3]
                hop.asn = getASN(hop.addr)
            }
        }
    }

    if (verbose)
        println ("${records.size()} records collected.")
    if (reqType.equals("traceroute_list")) {
        exportToJSON("IPv4/traceroute_list.json", records, null, verbose)
    } else if (reqType.equals("rtt_calculation")) {
        def rtt_calcs = rtt_calculation(records, verbose)
        exportToJSON("IPv4/rtt_calculation.json", null, rtt_calcs, verbose)
    }
    return 1
}

// method to compare two integers with a given operator
boolean compare(String operatore, int a, int b) {
    switch(operatore) {
        case "<":
            return a < b
        case "<=":
            return a <= b
        case ">":
            return a > b
        case ">=":
            return a >= b
        case "=": case "==":
            return a == b
        case "!=":
            return a != b
        default:
            return false
    }
}

// method to geolocate an IP address using the RIPEstat API
def geoloc(String addr, boolean coord, ArrayList<GeoCacheEntry> geoCache) {

    def cachedResponse = geoCache.find {it.address == addr}
    if (cachedResponse) // cache hit returns true
        return cachedResponse.data
    def url = "https://stat.ripe.net/data/maxmind-geo-lite/data.json?resource=" + addr + "&sourceapp=GeoDSL"
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("GET")

    if (conn.responseCode == 200) {
        def jsonResponse = new JsonSlurper().parse(new BufferedReader(new InputStreamReader(conn.inputStream)))
        conn.inputStream.close()
        conn.disconnect()
        def locatedResources = jsonResponse.data?.located_resources
        if (locatedResources && !locatedResources.isEmpty()) {
            def locations = locatedResources[0]?.locations
            if (locations && !locations.isEmpty()) {
                def location = locations[0]
                def country = location.country ?: "N/A"
                def city = location.city ?: "N/A"
                def lat = coord ? location.latitude : 0.0
                def lon = coord ? location.longitude : 0.0
                def response = [country, city, lat, lon]

                geoCache.add(new GeoCacheEntry(addr, response)) // caching the response
                return response
            }
        }
    } else {
        conn.disconnect()
    }
    
    return ["N/A", "N/A", 0.0, 0.0]
}

def getASN(String addr) {
    def url = "https://stat.ripe.net/data/prefix-overview/data.json?resource=" + addr + "&sourceapp=GeoDSL"
    def conn = new URL(url).openConnection()
    conn.setRequestMethod("GET")

    if (conn.responseCode == 200) {
        def jsonResponse = new JsonSlurper().parse(new BufferedReader(new InputStreamReader(conn.inputStream)))
        conn.inputStream.close()
        conn.disconnect()
        def data = jsonResponse.data
        if (data) {
            def asns = data.asns
            if (asns && !asns.isEmpty()) {
                return asns[0].asn == null ? "-1" : asns[0].asn
            }
        }
    } else {
        return "-1"
        conn.disconnect()
    }
}

def isWithinRadius(Double sLat, Double sLon, Double lat, Double lon, Double rad) { // using Haversine formula 
    def R = 6371 // radius of the Earth in km
    def dLat = Math.toRadians(lat - sLat)
    def dLon = Math.toRadians(lon - sLon)
    def a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(sLat)) * Math.cos(Math.toRadians(lat)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    def c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    def d = R * c
    return d <= rad
}

def limit_filter(ArrayList<Record> records, int source_limit, int target_limit, boolean verbose) {
    def prevs = []
    int count = 0
    if (records.isEmpty()) {
        println("No records")
        return
    }
    
    if (source_limit > 0) {
        if (verbose) println("Filtering sources: $source_limit limit.")
        records.each { r ->
            if (count < source_limit && !prevs.contains(r.src)) {
                    prevs.add(r.src)
                    count++
            }   
        }

        records.removeIf { r ->
            !prevs.contains(r.src)
        }
    }
    prevs = []
    count = 0

    if (target_limit > 0) {
        if (verbose) println ("Filtering targets: $target_limit limit.")
        records.each { r ->
            if (count < target_limit && !prevs.contains(r.dst)) {
                prevs.add(r.dst)
                count++
            }
        }

        records.removeIf { r ->
            !prevs.contains(r.dst)
        }
    }

    if (verbose) println ("${records.size()} records left after filtering.")
}

def exportToJSON(String path, List<?> records, LinkedHashMap<?, ?> calcs, boolean verbose) {
    def file = new File(path)
    if (!file.exists()) {
        file.createNewFile()
    }

    def writer = new FileWriter(file)
    def jrecords
    if (records != null) {
        jrecords = JsonOutput.toJson(records.collect { it.toMap() })
    } else {
        jrecords = JsonOutput.toJson(calcs)
    }

    writer.write(JsonOutput.prettyPrint(jrecords))
    writer.close()
    if(verbose) println("Records exported to $path.")
}

def rtt_calculation(ArrayList<Record> records, boolean verbose) {
    if (verbose) println ("Calculating RTT values.")
    if (!records || records.isEmpty()) {
        print ("No records collected.\n")
        return null
    }
    def avg_rtt, min_rtt, max_rtt, std_dev, counter
    counter = 0
    avg_rtt = 0.0
    min_rtt = Double.MAX_VALUE
    max_rtt = Double.MIN_VALUE
    std_dev = 0.0
    records.each { r ->
        r.hops.each { h ->
            counter++
            avg_rtt += h.rtt
            if (h.rtt < min_rtt) min_rtt = h.rtt
            if (h.rtt > max_rtt) max_rtt = h.rtt
        }
    }
    avg_rtt /= counter
    records.each { r ->
        r.hops.each { h ->
            std_dev += Math.pow(h.rtt - avg_rtt, 2)
        }
    }
    std_dev = Math.sqrt(std_dev / counter)
    if (verbose) println ("RTT values calculated.")

    def date = records[0].start.ftime.format('dd/MM/yyyy') // il primo record ha sicuramente la data d'inizio della misurazione
    // si usa quella come data di riferimento
    return ["data": date,"avg_rtt": avg_rtt, "min_rtt": min_rtt, "max_rtt": max_rtt, "std_dev": std_dev]
}