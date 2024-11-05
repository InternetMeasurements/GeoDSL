package it.unipi.DSL

import org.junit.runner.Request
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.CharBuffer
import java.security.MessageDigest

class Cache extends Thread{
    boolean done
    // caida
    boolean hit
    ReentrantLock lock = new ReentrantLock()
    Condition created = lock.newCondition()
    
    def waitUntilCreated(File file) {
        lock.lock()
        while (!file.exists()) {
            created.await()
        }
    }

    def notifyCreated() {
        lock.lock()
        try {
            created.signalAll()
        } finally {
            lock.unlock()
        }
    }
    
    //

    request r
    boolean getDone(){
        return done
    }
    Cache(request r){
        this.r = r
    }
    
    String url
    InputStream is
    StringBuilder sb

    boolean save = false
    boolean getSave(){
        return save
    }
    int index = 0 //indice che tiene conto del punto dove troncare file
    //in caso di annullamento del trasferimento
    void sendRequest(String name, InputStream content, StringBuilder ret){
        int count_open_square = 0
        int count_open_brace = 0
        try{
            int counter = 0
            boolean insidesquare = false
           
            MessageDigest md = MessageDigest.getInstance("MD5")
            md.update(name.getBytes())
            byte[] digest = md.digest()
            BigInteger no = new BigInteger(1, digest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            BufferedReader v = new BufferedReader(new InputStreamReader(content))
            
            FileWriter writer 
            if (r.isCacheOn()) 
            {
                new File("./cache").mkdirs()
                writer = new FileWriter("./cache/"+hashtext,true)
            }
            
            if (r.isCacheOn() && r.isDebugOn()) println("creating cachefile, url: "+name+" -- hashtext: "+hashtext)
            char[] tmp = new char[1000]
            int read = 1
            read = v.read(tmp,0,1000)
            while (read>0 && !isInterrupted()){
                 for (int i = 0; i < read; i++){
                        if (tmp[i] == '['){
                            count_open_square++
                            
                        }// devo controllare di non trovarmi dentro il campo "results"...
                        if (tmp[i] == ']'){
                            count_open_square--
                        }//...al momento del troncamento
                 
                        if (tmp[i] == '}' && count_open_square==1){
                            index = counter+1

                        }
                        counter++
                    }
                if (r.isCacheOn()) {
                   writer.write(tmp,0,read)
                }
                ret.append(tmp,0,read)
               
                tmp = new char[1000]
                read = v.read(tmp,0,1000)
            }
           
            if(r.isCacheOn()) writer.close()
            if (isInterrupted()) throw new InterruptedException()
        }
        catch (InterruptedException e){
            if (r.isDebugOn()) println("salvo il possibile")
            String tosave = sb.toString()
            tosave = tosave.substring(0,index)         
            if(index!=0)
                tosave+="]"
            sb.delete(0,sb.length())
            sb.append(tosave)
            if (r.isCacheOn()){
                MessageDigest md = MessageDigest.getInstance("MD5")
                md.update(name.getBytes())
                byte[] digest = md.digest()
                BigInteger no = new BigInteger(1, digest);
                String hashtext = no.toString(16);
                while (hashtext.length() < 32) {
                    hashtext = "0" + hashtext;
                }
                FileWriter f = new FileWriter("./cache/"+hashtext,false)
                f.write(tosave)
                println("hash "+hashtext)
                f.close()
            }
            save = true
            synchronized (r){
                r.notify()
            }
        }
        catch (IOException e){
            save = false
            sb.delete(0,sb.length())
            sb.append("errore")
            synchronized (r){
                r.notify()
            }
        }
    }

    boolean getCache(String url, StringBuilder ret){ // ret = bres 
        if(r.isCacheOn()){
            MessageDigest md = MessageDigest.getInstance("MD5")
            md.update(url.getBytes())
            byte[] digest = md.digest()
            BigInteger no = new BigInteger(1, digest)
            String hashtext = no.toString(16)
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            File f = new File("./cache/"+hashtext)
            if (!f.exists()) return false
            ret.append(f.text)
            if (r.isDebugOn()) println("found in cache, url: "+url+" -- hashtext: "+hashtext)
            return true
        }
        else {
            return false;
        }
    }

    void setParam(String url, InputStream i , StringBuilder s){
        this.url = url
        is = i
        sb = s
    }


    // caida caching submodule
    String hashGen(String path) {
        def query = new File(path).text
        def sortedText = query.split("\n").sort().join("\n")    
        MessageDigest md = MessageDigest.getInstance("MD5")
        md.update(sortedText.getBytes("UTF-8"))
        byte[] digest = md.digest()
        BigInteger bigInt = new BigInteger(1, digest)
        return bigInt.toString(16)
    }

    def getHit() {
        return hit
    }


    void run(){ //metodo necessario per la gestione della cache come thread

        if (r.getModule() == "RIPEAtlas") {
            done = false
            if(r.isDebugOn()) println("sono in run");
            sendRequest(url,is,sb)
           

            done = true
            synchronized (r){
                r.notify()
            }
            return
        }

        if (r.getModule() == "Caida") {
            done = false
            hit = false
            def hashtext = hashGen("script.txt")
            if (hashtext == null) 
                throw new Exception("Error generating hash")

            File cacheFile = new File("./cache/" + hashtext + ".json")
            if (cacheFile.exists()) {
                if (r.isDebugOn()) println("Cache hit")
                File inputFile = new File("IPv4/"+r.reqF+".json")
                inputFile.createNewFile()
                def writer = new FileWriter(inputFile)
                def jres = new JsonSlurper().parseText(cacheFile.text)
                writer.write(JsonOutput.prettyPrint(JsonOutput.toJson(jres)))
                writer.close()
                hit = true
                done = true
                print("Risultato caricato dalla cache.")

            } else {
                if (r.isDebugOn()) println("Cache miss")

                if (r.isDebugOn()) println("Creo il file cache")
                cacheFile.createNewFile()

                File inputFile = new File("IPv4/"+r.reqF+".json")

                waitUntilCreated(inputFile)
                def writer = new FileWriter(cacheFile)
                def jres = new JsonSlurper().parseText(inputFile.text)
                writer.write(JsonOutput.prettyPrint(JsonOutput.toJson(jres)))
                writer.close()
                
                if (r.isDebugOn()) println("Risultato cachato.")
                hit = false
                done = true
            }
            return
        }   
    }
}

