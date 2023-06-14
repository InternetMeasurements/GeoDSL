package it.unipi.DSL
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.text.SimpleDateFormat

class request {

    boolean isCacheOn(){
        return cache_on
    }
    boolean isDebugOn(){
        return debug
    }
    class measItems{ //struttura per recuperare dati di measurements
        Integer id
        Integer start_time
        String target_ip
        measItems(Integer i, Integer s, String t){
            id = i
            start_time = s
            target_ip = t
        }
    }
    class sourceData{ //struttura per recuperare dati su source probes
        Integer id
        String ip
        sourceData(Integer i, String s){
            id = i
            ip = s
        }
    }
    class container {
        List<pingList> data = new LinkedList<>()
    }
    class pingList{ // struttura per il salvataggio di una lista di ping
        String data
        Double ping
        pingList (String i, Double d){
            data = i
            ping = d
        }
        pingList (pingList d){
            this.data = d.data
            this.ping = d.ping
        }
    }

    boolean cache_on = true

    boolean debug = false

    def verbose = {String c -> //indica se si vuole visualizzare info di debug o meno
        switch (c){
            case "on":
                debug = true
                break
            case "off":
                debug = false
                break
        }

    }
    Integer wait = 10

    def max_wait = { Integer i -> //attesa massima per un risultato
        wait = i

    }
    List<String> targetIps = null

    void target_ips(String... c){ //indica quali sono i target ips
        targetIps = new LinkedList<>()
        for (int i = 0; i < c.length; i++){
            targetIps.add(c[i])
        }
    }

    String addr = "IPv4"

    String oneOff = "any"

    def frequency = { String f ->
        if (f.equals("one_off")) oneOff = "one_off"
        if (f.equals("periodic")) oneOff = "periodic"
    }

    def address = {String a ->
        if (a.equals("IPv6")) addr = a
        if (a.equals("IPv4")) addr = a
    }

    def cache = { String q ->
        if (q.equals("on")) cache_on = true
        if (q.equals("off")) cache_on = false
    }

    List<String> destProbesIps = new LinkedList<>()
    List<sourceData> sourceProbesIps = new LinkedList<>()
    List<measItems> measIds = new LinkedList<>()
    List<String> anchorsIps = new LinkedList<>()
    String cityN
    String fromWhat = null
    String toWhat = null
    int id = 0
    int source_limit = 10
    int meas_limit = 1
    int target_limit = 10
    //raggio e coordinate di source e target
    Integer dRad = 0
    Integer sRad = 0
    Double sLat = 0
    Double sLon = 0
    Double dLat = 0
    Double dLon = 0
    BigDecimal time_start = 0
    BigDecimal time_stop = 0
    String reqF = "" //operazione da eseguire sui dati
    boolean error = false
    def from = {what ->
        fromWhat = what

    }
    def to = {what->
        toWhat = what
    }
    def limit(n) {
        [on: {String s ->
            switch (s){
                case "measurements":
                    meas_limit = n
                    break
                case "sources":
                    source_limit = n
                    break
                case "targets":
                    target_limit = n
                    break
            }
        }]
    }
    //limit 10 on measurements equivale a limit(10).on(measurements)
    String sCountry = null
    String dCountry = null
    def source_coordinates (la, lo) {
            [radius : { r ->
                sLat = la
                sLon = lo
                sRad = r

            }]
    }
    def target_coordinates(la,lo)  {
            [radius : { r ->
                dLat = la
                dLon = lo
                dRad = r

            }]
    }
    def source_country = { String c ->
        if (sLat != 0 || sLon != 0){
            println("Warning: source_coordinates ha precedenza su source_country")
        }
            sCountry = c
    }
    def target_country = { String c ->
        if (dLat != 0 || dLon != 0){
            println("Warning: target_coordinates ha precedenza su target_country")
        }
        dCountry = c
    }
    void timeframe (String t, String f){
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        Date date = format.parse(t)
        long timestamp = date.getTime()
        switch (f){
            case "start":
                time_start = timestamp/1000
                break
            case "stop":
                time_stop = timestamp/1000
                break
        }
    }
    void obtain(String r){
        reqF = r
    }
    synchronized String sendRequestWithCacheCheck(String url2, StringBuilder bres){
        String res
        if (debug) println("creo thread")
        Cache c = new Cache(this)
        if (!c.getCache(url2,bres)){
            def get = new URL(url2).openConnection()
            def getRC = get.getResponseCode()
            if (getRC.equals(200)){
                c.setParam(url2,get.getInputStream(),bres)
                if (debug) println("faccio partire thread")
                c.start()
                wait(wait*1000)
                if (!c.getDone()){
                    if (debug) println("file risultati per "+url2+" troppo grosso, interrompo")
                    c.interrupt()
                    wait(wait*1000)
                    if (c.getSave()){
                        res = bres.toString()
                    }
                    else res = "errore"
                }
                else{
                    if (debug) println("tutto ok")
                    res = bres.toString()
                }
            }
            else{
                res = "errore"
            }
        }
        else res = bres.toString()
        return res
    }

    void getIps(LinkedList<?> p, String s){//recupera indirizzi e id delle probes che rispettano le specifiche
        if (debug) println("ips")
        if (s.equals("dest") && targetIps != null) return
        String res
        StringBuilder bres = new StringBuilder()
        def jsonSlurper = new JsonSlurper()
        String url2 = "https://atlas.ripe.net/api/v2/probes/?format=json&status_name=Connected"
        if (s.equals("source")){
            if ((sLat != 0 || sLon != 0) && sRad != 0) url2+= "&radius="+sLat+","+sLon+":"+sRad
            else if (sCountry != null) url2+="&country_code="+sCountry
            if (fromWhat.equals("anchors")) url2+="&is_anchor=true"
        }
        else if (s.equals("dest")){
            if ((dLat != 0 || dLon != 0) && dRad != 0) url2+= "&radius="+dLat+","+dLon+":"+dRad
            else if (dCountry != null) url2+="&country_code="+dCountry
            if (toWhat.equals("anchors")) url2+="&is_anchor=true"
        }
        if (debug) println(url2)
        res = sendRequestWithCacheCheck(url2,bres)
        if (res.equals("errore") || res.equals("")){
            return
        }
        def object = jsonSlurper.parseText(res)
        if (object.count != 0) {
            int count = 0
            int limit
            if (s.equals("source")) limit = source_limit
            else limit = target_limit
            while (count < limit){
                for (int i = 0; i< object.results.size() && count < limit; i++){
                    if (!object.results[i].address_v4.equals(null)) {
                        if (s.equals("source")){
                            if (addr.equals("IPv4")){
                                if (debug) println(s+" "+object.results[i].address_v4+", id "+object.results[i].id)
                                sourceData z = new sourceData(object.results[i].id,object.results[i].address_v4)
                                p.add(z)
                            }
                            else {
                                if (debug) println(s+" "+object.results[i].address_v6+", id "+object.results[i].id)
                                sourceData z = new sourceData(object.results[i].id,object.results[i].address_v6)
                                p.add(z)
                            }

                            count++
                        }
                        else {
                            if (addr.equals("IPv4")){
                                if (debug) println(s+" "+object.results[i].address_v4+", id "+object.results[i].id)
                                p.add(object.results[i].address_v4)
                            }
                            else{
                                if (debug) println(s+" "+object.results[i].address_v6+", id "+object.results[i].id)
                                p.add(object.results[i].address_v6)
                            }
                            count++
                        }
                    }
                }
                if (debug) println("count = "+count)
                if (count >= limit) break
                if (object.next != null){ //vado alla prossima pagina di risultati
                    url2 = object.next
                    res = sendRequestWithCacheCheck(url2,bres)
                    object = jsonSlurper.parseText(res)
                }
                else {
                    if (debug) println("numero risultati trovati per "+s+" minore del limite imposto")
                    break
                }
            }
        } //get ips
        else {
            if (debug) println("nessun risultato trovato per "+s)
        }
    }

    void getMeas(LinkedList<?> meas, LinkedList<?> ips, String type){// recupera i measurements che hanno come target membri della lista ips
        if (ips.isEmpty()) {
            if (debug) println("Non ci sono ip di probes target")
            return
        }
        if (debug) println("start of measurements")
        String res
        StringBuilder bres = new StringBuilder()
        ListIterator<String> ipIter
        if (targetIps != null){ //do la precedenza ai targetIps se sono stati settati dall'utente
            ipIter = targetIps.listIterator()
        }
        else ipIter = ips.listIterator()
        def jsonSlurper = new JsonSlurper()
        for (int i = 0; ipIter.hasNext(); i++ ){
            bres = new StringBuilder()
            String url2 = "https://atlas.ripe.net/api/v2/measurements/?format=json&target_ip="+ipIter.next()
            if (oneOff.equals("one_off")) url2+="&is_oneoff=true"
            else if (oneOff.equals("periodic")) {
                url2+="&is_oneoff=false"
            }
            if (type != null) url2+="&type="+type
            if (time_start != 0) url2+="&start_time__gte="+time_start
            if (time_stop != 0) url2+="&stop_time__lte="+time_stop
            res = sendRequestWithCacheCheck(url2,bres)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if(object.count != 0){
                int count = 0
                while (count < meas_limit){
                    for(int j = 0; j < object.results.size() && count < meas_limit;j++){
                        measItems m = new measItems(object.results[j].id,object.results[j].start_time,object.results[j].target_ip)
                        if (debug) println("adding id: "+object.results[j].id+" -- start_time: "+object.results[j].start_time)
                        meas.add(m)
                        count++
                        if (debug) println("numMeas: "+(count))
                    }
                    if (count >= meas_limit) break
                    if (object.next != null){ //vado alla prossima pagina di risultati
                        bres = new StringBuilder()
                        url2 = object.next
                        res = sendRequestWithCacheCheck(url2,bres)
                        object = jsonSlurper.parseText(res)
                    }
                    else {
                        if (debug) println("numero di measurements trovati è inferiore al limite imposto")
                        break
                    }
                }
            }
        }//get a measurement for each ip
    }

    Double getAvgPing(LinkedList<?> p, LinkedList<?> s) {
        if (p.isEmpty()){
            if (debug) println("Non ci sono measurements")
            return -1
        }
        if (s.isEmpty()){
            if (debug) println("Non ci sono ip di probe source")
            return -1
        }
        if (debug) println("start of results")
        StringBuilder bres = new StringBuilder()
        String res
        ListIterator<measItems> measIt = p.listIterator()
        ListIterator<sourceData> sipsIt = s.listIterator()
        List<Double> avgs = new LinkedList<>()
        List<Double> tmpAvg = new LinkedList<>()
        def jsonSlurper = new JsonSlurper()
        for (int i = 0; i < p.size(); i++){
            bres = new StringBuilder()
            measItems m = measIt.next()
            String url2 = "https://atlas.ripe.net/api/v2/measurements/"+m.id+"/results/?format=json&probe_ids="
            sipsIt = s.listIterator()
            while (sipsIt.hasNext()){
                sourceData tmp = sipsIt.next()
                url2+=tmp.id
                if (sipsIt.hasNext()) url2+=","
            }
            if (time_start != 0 && time_start > m.start_time) url2 = url2 + "&start=" + time_start
            else url2 = url2 + "&start=" + m.start_time
            if (time_stop != 0 && time_stop > m.start_time) url2 = url2 + "&stop=" + time_stop
            else url2 = url2 + "&stop=" + (m.start_time+1)
            if (debug) println(url2)
            res = sendRequestWithCacheCheck(url2,bres)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if (debug) println(object.size())
            if(object.size() != 0){
                for (int j = 0; j < object.size(); j++){
                    while (sipsIt.hasNext()){
                        sourceData tmp = sipsIt.next()
                        if (Double.valueOf(object[j].avg) > 0) {
                            tmpAvg.add( Double.valueOf(object[j].avg))
                            if (debug) println("id of measurement: "+m.id+", result: "+Double.valueOf(object[j].avg))
                        }
                        break
                    }
                    sipsIt = s.listIterator()
                }
                if (!tmpAvg.isEmpty()) avgs.add(tmpAvg.average())
                tmpAvg.clear()
            }
        }
        if (avgs.isEmpty()){
            println("Non è stato trovato nessun risultato conforme ai parametri di ricerca specificati")
            return -1
        }
        else return avgs.average()
    }

    void getPingList(LinkedList<?> p, LinkedList<?> s, container c){
        if (p.isEmpty()){
            if (debug) println("Non ci sono measurements")
            return
        }
        if (s.isEmpty()){
            if (debug) println("Non ci sono ip di probe source")
            return
        }
        if (debug) println("start of results")
        StringBuilder bres
        String res
        ListIterator<measItems> measIt = p.listIterator()
        ListIterator<sourceData> sipsIt = s.listIterator()
        def jsonSlurper = new JsonSlurper()
        for (int i = 0; i < p.size(); i++){
            bres = new StringBuilder()
            measItems m = measIt.next()
            String url2 = "https://atlas.ripe.net/api/v2/measurements/"+m.id+"/results/?format=json&probe_ids="
            sipsIt = s.listIterator()
            while (sipsIt.hasNext()){
                sourceData tmp = sipsIt.next()
                url2+=tmp.id
                if (sipsIt.hasNext()) url2+=","
            }
            if (time_start != 0 && time_start > m.start_time) url2 = url2 + "&start=" + time_start
            else url2 = url2 + "&start=" + m.start_time
            if (time_stop != 0 && time_stop > m.start_time) url2 = url2 + "&stop=" + time_stop
            else url2 = url2 + "&stop=" + (m.start_time+1)
            if (debug) println(url2)
            res = sendRequestWithCacheCheck(url2,bres)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if (debug) println(object.size())
            if(object.size() != 0){
                for (int j = 0; j < object.size(); j++){
                    while (sipsIt.hasNext()){
                        sourceData tmp = sipsIt.next()
                        if (Double.valueOf(object[j].avg) > 0) {
                            Date date = new Date((long)object[j].timestamp*1000)
                            Calendar calendar = Calendar.getInstance()
                            calendar.setTime(date)
                            c.data.add((new pingList(""+calendar.get(calendar.DAY_OF_MONTH)+"/"+calendar.get(calendar.MONTH)+"/"+calendar.get(calendar.YEAR),object[j].avg)))
                            if (debug) println("id of measurement: "+m.id+", result: "+Double.valueOf(object[j].avg))
                        }
                        break
                    }
                    sipsIt = s.listIterator()
                }
            }
        }
    }

    void id(int i){
        id = i
    }
    def execute = {
        if (reqF.equals("avg_ping")){
            Double result
            if (debug) println("targets")
            getIps(destProbesIps, "dest")
            if (debug) println("sources")
            getIps(sourceProbesIps, "source")
            getMeas(measIds, destProbesIps, "ping")
            result = getAvgPing(measIds, sourceProbesIps)
            println("fine: "+result)
        }
        else if (reqF.equals("ping_list")){
            if (debug) println("targets")
            getIps(destProbesIps, "dest")
            if (debug) println("sources")
            getIps(sourceProbesIps, "source")
            getMeas(measIds, destProbesIps, "ping")
            container c = new container()
            getPingList(measIds, sourceProbesIps, c)
            FileWriter f = new FileWriter("results.txt", true)
            String towrite = JsonOutput.toJson(c.data)
            f.write(towrite)
            f.close()
        }
        else if (reqF.equals("measurement_list")){
            if (debug) println("targets")
            getIps(destProbesIps, "dest")
            if (debug) println("sources")
            getIps(sourceProbesIps, "source")
            getMeas(measIds, destProbesIps, null)
            FileWriter f = new FileWriter("results.txt", true)
            String towrite = JsonOutput.toJson(measIds)
            f.write(towrite)
            f.close()
        }
    }

}

