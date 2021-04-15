![](https://github.com/HerculesCRUE/ib-asio-docs-/blob/master/images/logos_feder.png)

| Entregable     | Servicio API de validación de RDF                            |
| -------------- | ------------------------------------------------------------ |
| Fecha          | 15/04/2021                                                   |
| Proyecto       | [ASIO](https://www.um.es/web/hercules/proyectos/asio) (Arquitectura Semántica e Infraestructura Ontológica) en el marco de la iniciativa [Hércules](https://www.um.es/web/hercules/) para la Semántica de Datos de Investigación de Universidades que forma parte de [CRUE-TIC](https://www.crue.org/proyecto/hercules/) |
| Módulo         | Librería de validación de RDF                  |
| Tipo           | Software                                                     |
| Objetivo       | Servicio web en formato API para la validación de RDF |
| Estado         | **100%** Todos los casos de usos de este sistema han sido implementados, así como la documentación asociada. |

# Hércules RDF Validator Back

RDF playground. This repository contains the server part of the Hércules RDF Validator Back web app.
The server has been implemented in Scala using the [http4s](https://http4s.org/) library. 

# More info

* The client part of Hércules RDF Validator Back has been separated to a [React app](https://github.com/weso/rdfshape-client).
* Background info about validating RDF: [Validating RDF data book](http://book.validatingrdf.com)
* [How-to](https://github.com/labra/rdfshape/wiki/Tutorial) explains how to use Hércules RDF Validator Back to validate RDF

# Deployed versions of Hércules RDF Validator Back

Hércules RDF Validator Back is already deployed [here](http://rdfshape.weso.es).

# Installation and Local Deployment 

## Requirements

* Hércules RDF Validator Back server requires [SBT](https://www.scala-sbt.org/) to be built

## Deploy at local machine

* Clone the [github repository](https://github.com/labra/rdfshape)

* Go to directory where Hércules RDF Validator Back source code is located and execute `sbt run`

* After some time downloading and compiling           uri(
the source code it will start the application, which can be accessed at: [localhost:8080](http://localhost:8080)

* To use a different port run `sbt "run --server --port <PortNumber>"`

## Build a docker image

* Install [SBT](https://www.scala-sbt.org/)

* Run `sbt docker:publishLocal` which will create a docker file at `target/docker`

# Dependencies

Hércules RDF Validator Back server has been implemented in Scala using the following libraries:

* [SHaclEX](https://github.com/labra/shaclex): a Scala implementation of ShEx and SHACL.
* [http4s](https://http4s.org/): a purely functional library for http.
* [cats](https://typelevel.org/cats/): a library for functional programming in Scala.
* [UMLShaclex](https://github.com/labra/shaclex): contains the visualization code that converts schemas to UML diagrams
* [SRDF](http://www.weso.es/srdf/): is the library used to handle RDF. It is a simple interface with 2 implementations, one in [Apache Jena](https://jena.apache.org/), and the other in [RDF4j](https://rdf4j.org/).
* [Any23](https://any23.apache.org/): is used by Hércules RDF Validator Back to convert HTML files in RDFa and Microdata to RDF.
* [Topbraid SHACL API](https://github.com/TopQuadrant/shacl): is used to add another SHACL engine apart of the SHaclEX and Apache Jena SHACL engines.

# Contribution and issues

Contributions are greatly appreciated. Please fork this repository and open a pull request to add more features or submit issues:

* [Issues about Hércules RDF Validator Back online demo](https://github.com/labra/rdfshape/issues)
* [Issues about SHACLex validation library](https://github.com/labra/shaclex/issues)

<a href="https://github.com/weso/rdfshape/graphs/contributors">
  <img src="https://contributors-img.web.app/image?repo=weso/rdfshape" />
</a>
