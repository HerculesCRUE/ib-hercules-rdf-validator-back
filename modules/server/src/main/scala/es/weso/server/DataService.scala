package es.weso.server

import cats.effect._
import es.weso._
import es.weso.schema._
import es.weso.server.QueryParams._
import org.http4s._
import org.http4s.multipart._
import org.http4s.twirl._
import org.http4s.implicits._
import org.http4s.circe._
import cats.implicits._
import es.weso.server.ApiHelper._
import es.weso.server.Defaults._
import es.weso.server.helper.{DataFormat, Svg}
import io.circe.Json
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.log4s.getLogger

class DataService[F[_]](blocker: Blocker,
                        client: Client[F])(implicit F: Effect[F], cs: ContextShift[F])
  extends Http4sDsl[F] {

  private val relativeBase = Defaults.relativeBase
  private val logger = getLogger
  private val apiDataUri = uri"/api/data"

  val routes = HttpRoutes.of[F] {

    case req@GET -> Root / "dataConversions" :?
      OptDataParam(optData) +&
      OptDataURLParam(optDataURL) +&
      DataFormatParam(maybeDataFormat) +&
      InferenceParam(optInference) +&
      OptEndpointParam(optEndpoint) +&
      OptActiveDataTabParam(optActiveDataTab) +&
      TargetDataFormatParam(maybeTargetDataFormat) => {
      val either: Either[String, (Option[DataFormat],Option[DataFormat])] =  for {
         df <- maybeDataFormat.map(DataFormat.fromString(_)).sequence
         tdf <- maybeTargetDataFormat.map(DataFormat.fromString(_)).sequence
       } yield (df, tdf)

      either match {
        case Left(str) => BadRequest(str)
        case Right(values) => {
          val (optDataFormat,optTargetDataFormat) = values
          val dp =
            DataParam(optData, optDataURL, None, optEndpoint,
              optDataFormat,
              optDataFormat, optDataFormat,
              None,  //no dataFormatFile
              optInference,
              optTargetDataFormat,
              optActiveDataTab)

          val dv = DataValue(optData,
            optDataURL,
            optDataFormat.getOrElse(defaultDataFormat),
            availableDataFormats,
            optInference.getOrElse(defaultInference),
            availableInferenceEngines,
            optEndpoint,
            optActiveDataTab.getOrElse(defaultActiveDataTab)
          )
          val (maybeStr, eitherRDF) = dp.getData(relativeBase)
          println(s"GET dataConversions: $maybeStr\nEitherRDF:${eitherRDF}\ndp: ${dp}\ndv: $dv")
          val multipart = Multipart(Vector(
            Part.formData[F]("data",dp.data.getOrElse("")),
            Part.formData[F]("dataFormat", dp.dataFormat.map(_.name).getOrElse(DataFormats.defaultFormatName)),
            Part.formData[F]("targetDataFormat", dp.targetDataFormat.map(_.name).getOrElse(DataFormats.defaultFormatName))
          ))
          val entity = EntityEncoder[F,Multipart[F]].toEntity(multipart)
          val body = entity.body
          val req = Request(method = POST, uri = apiDataUri / "convert", body = body, headers = multipart.headers)
/*          val result: Option[Json] =
            if (allNone(optData,optDataURL,optEndpoint))
            None
            else
             Some(
              Json.fromFields(List(
                ("href", Json.fromString(
                  uri"/api/data/convert".
                  withOptionQueryParam(QueryParams.data,dp.data).
                  withOptionQueryParam(QueryParams.dataFormat, dp.dataFormat.map(_.name)).
                  withOptionQueryParam(QueryParams.targetDataFormat, dp.targetDataFormat.map(_.name)).renderString)
                )
              ))) */
          println(s"Request: $req")
          for {
            either <- client.fetch(req) {
              case Status.Successful(r) => r.attemptAs[Json].leftMap(_.message).value
              case r => r.as[String].map(b => s"Request $req failed with status ${r.status.code} and body $b".asLeft[Json])
            }
           resp <- Ok(html.dataConversions(either, dv,optTargetDataFormat.getOrElse(defaultDataFormat)))
          } yield resp
        }
      }
    }

    case req@POST -> Root / "dataConversions" => {
      req.decode[Multipart[F]] { m =>
        val partsMap = PartsMap(m.parts)
        for {
          maybeData <- DataParam.mkData(partsMap, relativeBase).value
          response <- maybeData match {
            case Left(msg) => BadRequest(s"Error obtaining data: $msg")
            case Right((rdf,dp)) => {
              val targetFormat = dp.targetDataFormat.getOrElse(defaultDataFormat)
              val dv = DataValue(
                dp.data, dp.dataURL,
                dp.dataFormat.getOrElse(defaultDataFormat), availableDataFormats,
                dp.inference.getOrElse(defaultInference), availableInferenceEngines,
                dp.maybeEndpoint,
                dp.activeDataTab.getOrElse(defaultActiveDataTab)
              )
              val multipart = Multipart(Vector(
                    Part.formData[F]("data",dp.data.getOrElse("")),
                    Part.formData[F]("dataFormat", dp.dataFormat.map(_.name).getOrElse(DataFormats.defaultFormatName)),
                    Part.formData[F]("targetDataFormat", dp.targetDataFormat.map(_.name).getOrElse(DataFormats.defaultFormatName))
              ))
              val entity = EntityEncoder[F,Multipart[F]].toEntity(multipart)
              val body = entity.body
              val req = Request(method = POST, uri = apiDataUri / "convert", body = body, headers = multipart.headers)
              for {
                either <- client.fetch(req) {
                  case Status.Successful(r) => r.attemptAs[Json].leftMap(_.message).value
                  case r => r.as[String].map(b => s"Request $req failed with status ${r.status.code} and body $b".asLeft[Json])
                }
                resp <- Ok(html.dataConversions(either, dv, dp.targetDataFormat.getOrElse(defaultDataFormat)))
              } yield resp

            }
          }
        } yield response
      }
    }

    case req@GET -> Root / "dataInfo" :?
      OptDataParam(optData) +&
      OptDataURLParam(optDataURL) +&
      DataFormatParam(maybeDataFormat) +&
      InferenceParam(optInference) +&
      OptEndpointParam(optEndpoint) +&
      OptActiveDataTabParam(optActiveDataTab) => {
      val either: Either[String, Option[DataFormat]] =  for {
        df <- maybeDataFormat.map(DataFormat.fromString(_)).sequence
      } yield df

      either match {
        case Left(str) => BadRequest(str)
        case Right(optDataFormat) => {
          val dp =
            DataParam(optData, optDataURL, None, optEndpoint,
              optDataFormat, optDataFormat, optDataFormat,
              None,  //no dataFormatFile
              optInference,
              None, optActiveDataTab)
          val (maybeStr, eitherRDF) = dp.getData(relativeBase)
          eitherRDF.fold(
            str => BadRequest(str),
            rdf => {
              val dv = DataValue(optData,
                optDataURL,
                optDataFormat.getOrElse(defaultDataFormat),
                availableDataFormats,
                optInference.getOrElse(defaultInference),
                availableInferenceEngines,
                optEndpoint,
                optActiveDataTab.getOrElse(defaultActiveDataTab)
              )
              Ok(html.dataInfo(Some(dataInfo(rdf, maybeStr, optDataFormat)),dv))
            })
        }
      }
    }

    case req@POST -> Root / "dataInfo" => {
      req.decode[Multipart[F]] { m =>
        val partsMap = PartsMap(m.parts)
        for {
          maybeData <- DataParam.mkData(partsMap,relativeBase).value
          response <- maybeData match {
            case Left(str) =>
              BadRequest (s"Error obtaining data: $str")
            case Right((rdf,dp)) => {
              val dv = DataValue(
                dp.data, dp.dataURL,
                dp.dataFormat.getOrElse(defaultDataFormat), availableDataFormats,
                dp.inference.getOrElse(defaultInference), availableInferenceEngines,
                dp.maybeEndpoint,
                dp.activeDataTab.getOrElse(defaultActiveDataTab)
              )
              Ok(html.dataInfo(Some(dataInfo(rdf,dp.data,dp.dataFormat)),dv))
            }
          }
        } yield response
      }
    }

    case req@GET -> Root / "dataVisualization" :?
      OptDataParam(optData) +&
        OptDataURLParam(optDataURL) +&
        DataFormatParam(maybeDataFormat) +&
        InferenceParam(optInference) +&
        OptEndpointParam(optEndpoint) +&
        OptActiveDataTabParam(optActiveDataTab) +&
        TargetDataFormatParam(maybeTargetDataFormat) => {

        val either: Either[String, (Option[DataFormat], Option[DataFormat])] = for {
          df  <- maybeDataFormat.map(DataFormat.fromString(_)).sequence
          tdf <- maybeTargetDataFormat.map(DataFormat.fromString(_)).sequence
        } yield (df, tdf)

        either match {
          case Left(str) => BadRequest(str)
          case Right(values) => {
            val (optDataFormat, optTargetDataFormat) = values
            val targetDataFormat = optTargetDataFormat.getOrElse(Svg)
            if (allNone(optData, optDataURL, optEndpoint)) {
              val dv = DataValue(
                optData,
                optDataURL,
                optDataFormat.getOrElse(defaultDataFormat),
                availableDataFormats,
                optInference.getOrElse(defaultInference),
                availableInferenceEngines,
                optEndpoint,
                optActiveDataTab.getOrElse(defaultActiveDataTab)
              )
              Ok(html.dataVisualization(dv, targetDataFormat))
            } else {
            val dp = DataParam(optData,
                               optDataURL,
                               None,
                               optEndpoint, optDataFormat,
                               optDataFormat,
                               optDataFormat,
                               None,
                               optInference,
                               None,
                               optActiveDataTab)
            val (maybeStr, eitherRDF) = dp.getData(relativeBase)
            eitherRDF.fold(
              str => BadRequest(str),
              rdf => {
                val targetFormat = dp.targetDataFormat.getOrElse(Svg)
                val dataFormat = dp.dataFormat.getOrElse(defaultDataFormat)
                DataConverter
                  .rdfConvert(rdf,dp.data,dataFormat,targetFormat.name)
                  .fold(
                    e => BadRequest(s"Error in conversion to $targetDataFormat: $e\nRDF:\n${rdf.serialize("TURTLE")}"),
                    result => Ok(result.toJson).map(_.withContentType(`Content-Type`(targetDataFormat.mimeType)))
                  )

              }
            )
          }
        }
      }
    }


    case req@POST -> Root / "dataVisualization" => {
      req.decode[Multipart[F]] { m =>
        val partsMap = PartsMap(m.parts)
        for {
          maybeData <- DataParam.mkData(partsMap,relativeBase).value
          response <- maybeData match {
            case Left(str) => BadRequest (s"Error obtaining data: $str")
            case Right((rdf,dp)) => {
/*              val dv = DataValue(
                dp.data, dp.dataURL,
                dp.dataFormat.getOrElse(defaultDataFormat), availableDataFormats,
                dp.inference.getOrElse(defaultInference), availableInferenceEngines,
                dp.endpoint,
                dp.activeDataTab.getOrElse(defaultActiveDataTab)
              ) */
              val targetFormat = dp.targetDataFormat.getOrElse(Svg)
              val dataFormat = dp.dataFormat.getOrElse(defaultDataFormat)
              DataConverter.rdfConvert(rdf,dp.data,dataFormat,targetFormat.name).
                fold(e => BadRequest(s"Error in conversion to $targetFormat: $e\nRDF:\n${rdf.serialize("TURTLE")}"),
                  result => Ok(result.toJson).map(_.withContentType(`Content-Type`(targetFormat.mimeType)))
                )
            }
          }
        } yield response
      }
    }

  }

  def err[A](str: String): Either[String, A] = {
    Left(str)
  }

  private[server] def validateResponse(result: Result,
                                       time: Long,
                                       dp: DataParam,
                                       sp: SchemaParam,
                                       tp: TriggerModeParam): F[Response[F]] = {
    val dv = DataValue(
      dp.data, dp.dataURL,
      dp.dataFormat.getOrElse(defaultDataFormat), availableDataFormats,
      dp.inference.getOrElse(defaultInference), availableInferenceEngines,
      dp.maybeEndpoint,
      dp.activeDataTab.getOrElse(defaultActiveDataTab)
    )
    val sv = SchemaValue(sp.schema,
      sp.schemaURL,
      sp.schemaFormat.getOrElse(defaultSchemaFormat), availableSchemaFormats,
      sp.schemaEngine.getOrElse(defaultSchemaEngine), availableSchemaEngines,
      sp.activeSchemaTab.getOrElse(defaultActiveSchemaTab)
    )
    val smv = ShapeMapValue(
      tp.shapeMap, tp.shapeMapURL,
      tp.shapeMapFormat.getOrElse(defaultShapeMapFormat),
      availableShapeMapFormats,
      tp.activeShapeMapTab.getOrElse(defaultActiveShapeMapTab)
    )
    
    val validationReport: Option[Either[String,String]] =
      Some(result.validationReport.flatMap(_.serialize("TURTLE")))

    logger.info(s"Validation report: $validationReport")

    Ok(html.validate(Some(result),time,
      validationReport,
      dv, sv,
      availableTriggerModes,
      tp.triggerMode.getOrElse(defaultTriggerMode),
      smv,
      getSchemaEmbedded(sp)
    ))
  }

  private def allNone(maybes: Option[String]*) = {
    maybes.forall(_.isEmpty)
  }

}

object DataService {
  def apply[F[_]: Effect: ContextShift](blocker: Blocker,
                                        client: Client[F]
                                       ): DataService[F] =
    new DataService[F](blocker,client)
}