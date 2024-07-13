package it.unipi.DSL
import groovy.transform.BaseScript
import org.codehaus.groovy.control.CompilerConfiguration

def config = new CompilerConfiguration()
config.scriptBaseClass = 'baseScript'
def shell = new GroovyShell(this.class.classLoader, config)
shell.evaluate(new File("/mnt/c/Users/giuli/OneDrive/Desktop/GeoDSL-main/src/main/groovy/it/unipi/DSL/script.txt").text)