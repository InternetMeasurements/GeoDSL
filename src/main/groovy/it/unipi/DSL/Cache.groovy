package it.unipi.DSL

import org.junit.runner.Request

import java.nio.CharBuffer
import java.security.MessageDigest

class Cache extends Thread{
    boolean done
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
    long index = 0 //indice che tiene conto del punto dove troncare file
    //in caso di annullamento del trasferimento
    void sendRequest(String name, InputStream content, StringBuilder ret){
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
                            insidesquare = true
                        }// devo controllare di non trovarmi dentro il campo "results"...
                        if (tmp[i] == ']'){
                            insidesquare = false
                        }//...al momento del troncamento
                        if (tmp[i] == '}' && !insidesquare){
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
            tosave = tosave.substring(0,(int)index)
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



    void run(){ //metodo necessario per la gestione della cache come thread

            done = false
            if(r.isDebugOn()) println("sono in run");
            sendRequest(url,is,sb)
           

            done = true
            synchronized (r){
                r.notify()
            }
            return




    }
}

