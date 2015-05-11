package controllers

import play.api._
import play.api.mvc._
import scala.concurrent._
import akka.actor._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.io.ByteArrayInputStream
import play.api._
import play.api.mvc._
import play.api.libs.Files._
import es.weso.shex.Schema
import scala.util._
import es.weso.rdf._
import es.weso.rdfgraph.nodes.IRI
import es.weso.rdf.jena._
import es.weso.monads.{Result => SchemaResult, Failure => SchemaFailure}
import es.weso.shex._
import es.weso.utils._
import es.weso.utils.TryUtils._
import es.weso.utils.RDFUtils._
import java.net.URL
import java.io.File
import es.weso.utils.IOUtils._
import Multipart._
import play.api.libs.json._

trait SchemaConverter { this: Controller => 

 def converterSchemaFuture(
          schema: String
        , inputFormat: String
        , outputFormat: String
    ) : Future[Try[String]]= {
       Future(Schema.fromString(schema,inputFormat).map(_._1.serialize(outputFormat)))
  }
  
  
  def convert_schema_get(
          schema: String
        , inputFormat: String
        , outputFormat: String
        ) = Action.async {  
        converterSchemaFuture(schema,inputFormat, outputFormat).map(output => {
              output match {
                case Success(result) => {
                  val schemaInput = SchemaInput(schema,inputFormat)
                  val vf = ValidationForm.fromSchemaConversion(schemaInput)
                  Ok(views.html.convert_schema(vf,outputFormat,result))
                }
                case Failure(e) => BadRequest(e.getMessage)
              }
          })
  }

  def convert_schema_post = Action { request => {
     val r = for ( mf <- getMultipartForm(request)
                 ; schemaInput <- parseSchemaInput(mf)
                 ; str_schema <- schemaInput.getSchemaStr
                 ; outputFormat <- parseKey(mf, "outputFormat")
                 ; schema <- schemaInput.getSchema(schemaInput.inputFormat)
                 ) yield (schemaInput, outputFormat,schema.serialize(outputFormat))
     
      r match {
       case Success((schemaInput, outputFormat,result)) => {
         val vf = ValidationForm.fromSchemaConversion(schemaInput)
         Ok(views.html.convert_schema(vf,outputFormat,result))
       }
             
             
       case Failure(e) => BadRequest(e.getMessage) 
      }
    } 
  }

  def schemaFormats = Action {
    Ok(Json.toJson(SchemaFormats.toList))
  }
    
}

object SchemaConverter extends Controller with SchemaConverter
