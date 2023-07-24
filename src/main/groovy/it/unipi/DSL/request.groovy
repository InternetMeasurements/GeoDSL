package it.unipi.DSL

import groovy.json.JsonOutput
import groovy.json.JsonGenerator
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
        String status
        Integer stop_time
        measItems(Integer i, Integer s,Integer sp,String st, String t){
            id = i
            start_time = s
            stop_time = sp
            target_ip = t
            status = st
           
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
    class pingList{ // struttura per il salvataggio di una lista di ping; potremmo aggiungere altre misurazioni oltre a quella media e altre informazioni sul ping
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
    class containerT{
        List<tracerouteList> data = new LinkedList<>()
    }

    class responseTrace{
        String ipresponse
        Double ttl 
        Integer size
        Double rtt 
        String x
       responseTrace (String ip, Double t, Integer s, Double r){
            ipresponse = ip
            ttl = t 
            size = s 
            rtt = r
        }

        responseTrace (String e){
            x = e
        }

    }

    class hop {
        Integer hop
        Integer asn
        Double lon 
        Double lat
        List<responseTrace> risposte 

        hop (Integer h, List<responseTrace> l,Integer a,Double lo,Double la){
            hop = h
            risposte = l
            asn = a
            lon = lo
            lat = la
        }

        hop (Integer h, List<responseTrace> l,Integer a){
            hop = h
            risposte = l
            asn = a
        }

        hop (Integer h, List<responseTrace> l,Double lo,Double la){
            hop = h
            risposte = l
            lon = lo
            lat = la
        }


        hop (Integer h, List<responseTrace> l){
            hop = h
            risposte = l
           
        }
    }



    
    class tracerouteList{ // struttura per il salvataggio di una lista di ping; potremmo aggiungere altre misurazioni oltre a quella media e altre informazioni sul ping
      
        String data
        String ipsource
        String iptarget
        List<hop> list_route
        tracerouteList (String d,String ips, String ipt, List<hop> l){

           data = d
           ipsource = ips
           iptarget = ipt
           list_route = l
            
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
    Integer wait_t = 10
    boolean wait_set = false
    
    def max_wait = { Integer i -> //attesa massima per un risultato
        wait_t = i
        wait_set = true

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
    int source_limit = 0  //  massimo numero di probes
    int meas_limit = 0 // numero massimo di measurment
    int target_limit = 0// massimo numero di probes
    int res_limit = 0 
    int star_limit = 0
    int hop_limit = 0
    //raggio e coordinate di source e target
    Integer dRad = 0
    Integer sRad = 0
    Double sLat = 0
    Double sLon = 0
    Double dLat = 0
    Double dLon = 0
    BigDecimal time_start = 0
    BigDecimal time_stop = 0
    boolean asn_on = false
    boolean coordinate_on = false
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
                case "results":
                    res_limit = n
                    break
                case "hop":
                    hop_limit = n
                    break
                case "star":
                    star_limit = n 
                    break
            }
        }]
    }

    def asn = { String q ->
        if (q.equals("on")) asn_on = true
        if (q.equals("off")) asn_on = false
    }

     def coordinate = { String q ->
        if (q.equals("on")) coordinate_on = true
        if (q.equals("off")) coordinate_on = false
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
    synchronized String sendRequestWithCacheCheck(String url2, StringBuilder bres){ // bres: contiene un StringBuilder per costruire il vettore di stringhe di risultati
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
               // if(wait_set){
                   
                    wait(wait_t*1000)
               /* }
                else{
                    if(debug) println("Wait unset")
                    wait()
                }*/
                if (!c.getDone()){
                    if (debug) println("file risultati per "+url2+" troppo grosso, interrompo")
                    c.interrupt()
                    wait(wait_t*1000)
                    if (c.getSave()){
                        res = bres.toString()
                    }
                    else res = "errore"
                }
                else{
                    if (debug) println("tutto ok")
                    res = bres.toString()
                    // println("Sono in srwcc"+res)
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
            else if (sCountry != null) url2+="&country_code="+sCountry // il controllo sulle nazioni e` gia presente
            if (fromWhat.equals("anchors")) url2+="&is_anchor=true"
        }
        else if (s.equals("dest")){
            if ((dLat != 0 || dLon != 0) && dRad != 0) url2+= "&radius="+dLat+","+dLon+":"+dRad
            else if (dCountry != null) url2+="&country_code="+dCountry
            if (toWhat.equals("anchors")) url2+="&is_anchor=true"
        }
        if (debug) println(url2)
        res = sendRequestWithCacheCheck(url2,bres)
        //println("sono in getIps"+res)
        if (res.equals("errore") || res.equals("")){
            return
        }
        def object = jsonSlurper.parseText(res)
        if (object.count != 0) {
            int count = 0
            int limit = object.count
            if (s.equals("source")) {if(source_limit!=0)limit = source_limit}
            else {if(target_limit!=0)limit = target_limit}
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
                    if (debug && (target_limit!=0 || source_limit!=0)) println("numero risultati trovati per "+s+" minore del limite imposto")
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
        Integer count_tot = 0
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

            if (time_stop !=0) {
                url2+="&start_time__lt="+time_stop // deve controllare anche i measurement che sono partiti prima di time_start
                url2+="&status=Ongoing,Stopped"
            }
            //if (time_stop != 0) url2+="&stop_time__lte="+time_stop+"&stop_time__gte="+time_start // ma lo stop time deve essere comunque nell' interevallo
            res = sendRequestWithCacheCheck(url2,bres)
            println("sono in getMeas: "+url2)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if(debug)println("numero di Meas trovati in totale: "+object.count)
            if(object.count != 0){
                int count = 0
                int limit = object.count
                if(meas_limit!=0) {limit = meas_limit}
                while (count < limit){
                    for(int j = 0; j < object.results.size() && count < limit;j++){

                          if(object.results[j].status.name == "Stopped" && time_start!=0 && time_stop!=0){  
                                                      
                                    if(object.results[j].stop_time >= time_start || object.results[j].status.when >= time_start){ // l'importante e` che il mesaurement si fermi dopo il time start
                                        measItems m = new measItems(object.results[j].id,object.results[j].start_time,object.results[j].stop_time,object.results[j].target_ip,object.results[j].status.name)
                                        if (debug) println("adding id: "+object.results[j].id+" -- start_time: "+object.results[j].start_time+" -- stop_time: "+object.results[j].stop_time+" -- status: "+object.results[j].status.name)
                                        meas.add(m)
                                        count++
                                        count_tot++
                                        if (debug) println("numMeas: "+(count))
                                    }else{
                                        if (debug) println("not adding id: "+object.results[j].id+" -- start_time: "+object.results[j].start_time+" -- stop_time: "+object.results[j].stop_time+" -- status: "+object.results[j].status.name)
                                    }
                                }
                            else {
                                measItems m = new measItems(object.results[j].id,object.results[j].start_time,object.results[j].stop_time,object.results[j].target_ip,object.results[j].status.name)
                                if (debug) println("adding id: "+object.results[j].id+" -- start_time: "+object.results[j].start_time+" -- stop_time: "+object.results[j].stop_time+" -- status: "+object.results[j].status.name)
                                meas.add(m)
                                count++
                                count_tot++
                                if (debug) println("numMeas: "+(count))
                            }
                        }
                    
                    if (count >= limit) break
                    if (object.next != null){ //vado alla prossima pagina di risultati
                        bres = new StringBuilder()
                        url2 = object.next
                        res = sendRequestWithCacheCheck(url2,bres)
                        object = jsonSlurper.parseText(res)
                    }
                    else {
                        if (debug && meas_limit!=0)  println("numero di measurements trovati è inferiore al limite imposto")
                        break
                    }
                }
            }
        }//get a measurement for each ip
        if(debug) println("Measurement totali: "+count_tot)
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
                 int count = 0
                 int limit = object.size()
                 if (res_limit!=0){
                    if(limit>res_limit)
                            limit = res_limit
                       
                 }
                while(count< limit){
                        for (int j = 0; j < object.size() && count < limit; j++){
                            while (sipsIt.hasNext()){
                                sourceData tmp = sipsIt.next()
                                if (Double.valueOf(object[j].avg) > 0) {
                                    tmpAvg.add( Double.valueOf(object[j].avg))
                                    count++
                                    if (debug) println("id of measurement: "+m.id+", result: "+Double.valueOf(object[j].avg))
                                }
                                break
                            }
                            sipsIt = s.listIterator()
                        }
                        if (!tmpAvg.isEmpty()) avgs.add(tmpAvg.average())
                        tmpAvg.clear()
                        if(count>=limit) break
                }
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
           // println("Sono dopo srwcc in PingList:"+res)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if (debug) println("Size: "+object.size())
            if(object.size() != 0){
                int count = 0
                int limit = object.size()
                 if (res_limit!=0){
                   if(limit>res_limit){ 
                            limit = res_limit
                   }
                 }
                while(count<limit){
                    for (int j = 0; j < object.size() && count<limit; j++){
                        while (sipsIt.hasNext()){
                            sourceData tmp = sipsIt.next()
                            if (Double.valueOf(object[j].avg) > 0) {
                                Date date = new Date((long)object[j].timestamp*1000)
                                Calendar calendar = Calendar.getInstance()
                                calendar.setTime(date)
                                c.data.add((new pingList(""+calendar.get(calendar.DAY_OF_MONTH)+"/"+calendar.get(calendar.MONTH)+"/"+calendar.get(calendar.YEAR),object[j].avg)))
                                count++
                                if (debug) println("id of measurement: "+m.id+", result: "+Double.valueOf(object[j].avg))
                            }
                            break
                        }
                        sipsIt = s.listIterator()
                    }
                    if(count>=limit) break
                }
            }
        }
    }

   void getTracerouteList(LinkedList<?> p, LinkedList<?> s, containerT c){ // agigustare il limit result
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
          ///  println("Sono dopo srwcc in PingList:"+res)
            if (res.equals("errore") || res.equals("")){
                continue
            }
            def object = jsonSlurper.parseText(res)
            if (debug) println("Size: "+object.size())
             if(object.size() != 0){
                int count = 0
                int limit = object.size()
                if (res_limit!=0){
                   if(limit>res_limit)
                        limit = res_limit
                } 
                
                    for (int j = 0; j < object.size() && count<limit; j++){
                        while (sipsIt.hasNext()){
                            sourceData tmp = sipsIt.next()
                            int count_star = 0
                            int asn
                            Double lat
                            Double lon
                            boolean star = false
                            List<hop> lista_hop = new LinkedList<>()


                            if(hop_limit!=0 && object[j].result.size()>hop_limit){
                                if(debug) println("Il numero di hop supera il limite imposto:"+object[j].result.size())
                                break
                            }

                            for(int k = 0;k<object[j].result.size();k++){
                                List<responseTrace> risposte = new LinkedList<>()
                                star = false
                                for(int l = 0;l<3;l++){
                                    if(object[j].result[k].result[l].x != null){
                                        risposte.add(new responseTrace(object[j].result[k].result[l].x))
                                        if(star_limit!=0 || asn_on || coordinate_on)star == true
                                    } else
                                    {
                                        risposte.add(new responseTrace(object[j].result[k].result[l].from,object[j].result[k].result[l].ttl,object[j].result[k].result[l].size,object[j].result[k].result[l].rtt))
                                    }
                                }
                                if(star_limit!=0 && star == true){
                                    count_star++
                                } 

                               if(!star && asn_on){
                                    StringBuilder bres2 = new StringBuilder()
                                    String urlAS = "https://stat.ripe.net/data/prefix-overview/data.json?resource="+object[j].result[k].result[0].from
                                    String res_as = sendRequestWithCacheCheck(urlAS,bres2)
                                    if(res_as.equals("errore")||res_as.equals(""))
                                       asn = -2
                                    else{
                                        def asn_obj = jsonSlurper.parseText(res_as)
                                        String str_tmp = asn_obj.data.asns.asn
                                        if(str_tmp.equals("[]")){
                                            asn = -1
                                        } else{
                                            str_tmp = str_tmp.replace('[','')
                                            str_tmp = str_tmp.replace(']','')
                                            asn = Integer.parseInt(str_tmp)
                                        }
                                    }
                                    
                                }

                                if(!star && coordinate_on){
                                    StringBuilder bres2 = new StringBuilder()
                                    String urlCOOR ="https://stat.ripe.net/data/maxmind-geo-lite/data.json?resource="+object[j].result[k].result[0].from
                                    String res_coor = sendRequestWithCacheCheck(urlCOOR,bres2)
                                    if(res_coor.equals("errore")||res_coor.equals("")){
                                        lat = Double.NaN 
                                        lon = Double.NaN
                                    }
                                    else{                                    
                                        def coor_obj = jsonSlurper.parseText(res_coor)
                                        String str_tmp = coor_obj.data.located_resources.locations.longitude
                                        if(str_tmp.equals("[]")){
                                            lon = Double.NaN
                                        }else{
                                            str_tmp = str_tmp.replace('[','')
                                            str_tmp = str_tmp.replace('[','')
                                            str_tmp = str_tmp.replace(']','')
                                            str_tmp = str_tmp.replace(']','')
                                            lon = Double.parseDouble(str_tmp)
                                        }
                                        
                                        str_tmp = coor_obj.data.located_resources.locations.latitude
                                        if(str_tmp.equals("[]")){
                                            lon = Double.NaN
                                        }else{
                                            str_tmp = str_tmp.replace('[','')
                                            str_tmp = str_tmp.replace('[','')
                                            str_tmp = str_tmp.replace(']','')
                                            str_tmp = str_tmp.replace(']','')
                                            lat = Double.parseDouble(str_tmp)
                                        }
                                    }
                                    
                                }

                                println("ASN:"+asn)
                                
                                if(coordinate_on && asn_on && !Double.isNaN(lat) && !Double.isNaN(lon) && asn != -2) 
                                    lista_hop.add(new hop(object[j].result[k].hop,risposte,asn,lon,lat))
                                else if(coordinate_on && !asn_on  && !Double.isNaN(lat) && !Double.isNaN(lon))
                                    lista_hop.add(new hop(object[j].result[k].hop,risposte,lon,lat))
                                else if(!coordinate_on && asn_on && asn != -2)
                                    lista_hop.add(new hop(object[j].result[k].hop,risposte,asn))
                                else            
                                    lista_hop.add(new hop(object[j].result[k].hop,risposte))

                            }

                            if(count_star>star_limit && star_limit!=0){
                                if(debug)println("Il numero di star supera il limite imposto:+"+count_star)
                                break
                            } 

                            count++
                            Date date = new Date((long)object[j].timestamp*1000)
                            Calendar calendar = Calendar.getInstance()
                            calendar.setTime(date)
                            c.data.add((new tracerouteList(""+calendar.get(calendar.DAY_OF_MONTH)+"/"+calendar.get(calendar.MONTH)+"/"+calendar.get(calendar.YEAR),object[j].src_addr,object[j].dst_addr,lista_hop)))
                            if (debug) println("id of measurement: "+m.id)
                            break
                        }
                        sipsIt = s.listIterator()
                       
                        if(count>=limit)break
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
            FileWriter f = new FileWriter("resultsPing_list.txt", true) // semplice salvataggio dei risultati in file diversi o aggiungere separatori per le varie misurazioni e tipologie
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
            FileWriter f = new FileWriter("resultsmeasuremnt_list.txt", true)
            String towrite = JsonOutput.toJson(measIds)
            f.write(towrite)
            f.close()
        } else if (reqF.equals("traceroute_list")){
            if (debug) println("targets")
            getIps(destProbesIps, "dest")
            if (debug) println("sources")
            getIps(sourceProbesIps, "source")
            getMeas(measIds, destProbesIps, "traceroute")
            containerT c = new containerT()
            getTracerouteList(measIds, sourceProbesIps, c)
            FileWriter f = new FileWriter("resultsTraceroute_list.txt", true)
            def generator = new JsonGenerator.Options().excludeNulls().build()
            String towrite = generator.toJson(c.data)
            f.write(towrite)
            f.close()
        }
    }

}

