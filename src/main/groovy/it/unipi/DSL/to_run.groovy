package it.unipi.DSL
import groovy.transform.BaseScript
import org.codehaus.groovy.control.CompilerConfiguration

def config = new CompilerConfiguration()
config.scriptBaseClass = 'baseScript'
def shell = new GroovyShell(this.class.classLoader, config)
shell.evaluate (new File("script.txt").text)
