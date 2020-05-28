import zio.{test => _, _}
import zio.duration._
import zio.random._
import zio.stream._
import zio.test._
import zio.test.environment._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.clock._

import scala.io.Source

object ScalaInTheCitySpec extends DefaultRunnableSpec {
  import ScalaInTheCity._

  final case class TestData(
      language: String,
      repo: Repo,
      contributors: List[Contributor]
  )

  val testData: Managed[Nothing, List[TestData]] =
    ZManaged
      .make {
        ZIO.effectTotal(Source.fromResource("data.txt"))
      } { source => ZIO.effectTotal(source.close) }
      .mapM { source =>
        ZIO.effectTotal {
          source.getLines.toList.init
            .map(_.split(','))
            .map {
              case Array(full_name, login, contributions) =>
                (
                  "scala",
                  Repo(full_name),
                  Contributor(login, contributions.toInt)
                )
            }
            .groupBy(data => (data._1, data._2))
            .map {
              case ((language, repo), grouped) =>
                TestData(language, repo, grouped.map(_._3))
            }
            .toList
        }
      }

  val testGithub: ZLayer[Has[List[TestData]], Nothing, Github] =
    ZLayer.fromService { testData =>
      new Github.Service {
        def getRepos(language: String, limit: Int): Task[List[Repo]] =
          Task(testData.filter(_.language == language).take(limit).map(_.repo))
        def getContributors(repo: ScalaInTheCity.Repo): Task[List[Contributor]] =
          Task(testData.find(_.repo == repo).get.contributors)
      }
    }

  val testEnvironment =
    testData.toLayer >>> testGithub

  val genContributor: Gen[Random, Contributor] =
    for {
      name          <- Gen.elements("a", "b", "c", "d", "e")
      contributions <- Gen.int(1, 100)
    } yield Contributor(name, contributions)

  def spec = suite("ScalaInTheCitySpec")(
    suite("unit tests")(
      test("notScalaSteward excludes Scala Steward") {
        val input = List(Contributor("scala-steward", 10))
        val output = notScalaSteward(input)
        assert(output)(isEmpty)
      }
    ),
    suite("property based tests")(
      testM("result is distinct") {
        check(Gen.listOf(genContributor)) { contributors =>
          val output = contributionsByUser(contributors)
          assert(output)(isDistinct)
        }
      },
      testM("result are sorted") {
        check(Gen.listOf(genContributor)) { contributors =>
          val output = contributionsByUser(contributors)
          assert(output.map(_.contributions))(isSortedReverse)
        }
      },
    ),
    suite("with test implementation of Github service")(
      testM("program yields expected result with test data") {
        val actual = topContributors("scala", 3)
        assertM(actual)(contains(Contributor("iravid", 141)))
      },
    ).provideCustomLayerShared(testEnvironment) @@ sequential,
    suite("with live implementation of Github service")(
      testM("program yields expected result with live service") {
        val loginResults = topContributors("scala", 1).map(_.map(_.login))
        assertM(loginResults)(contains("mateiz"))
      } @@ retry(Schedule.exponential(1.second) && Schedule.recurs(5))
    ).provideCustomLayerShared(liveEnvironment.orDie) @@ ifEnvSet("ci"),
  ) @@ timeout(60.seconds),
}
