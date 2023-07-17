
import it.unipi.DSL.request
import it.unipi.DSL.defs
import groovy.json.*

abstract class baseScript extends Script {

  def RIPEAtlas(@DelegatesTo(request) Closure cl) {
    def show = new request()
    def definitions = new defs()
    def code = cl.rehydrate(show, definitions, this)
    code.resolveStrategy = Closure.DELEGATE_FIRST
    code()
  }

}