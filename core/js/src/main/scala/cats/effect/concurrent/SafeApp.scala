/*
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats
package effect
package concurrent

import cats.effect.internals.Logger
import cats.implicits._
import scala.scalajs.js.annotation.JSExport

/** Safe `App` type that runs a [[ConcurrentEffect]] and exits with
  * the returned code.  If the concurrent effect raises an unhandled
  * error, then the stack trace is printed to standard error and the
  * process exits with code 1.
  * 
  * {{{
  * import cats.effect.IO
  * import cats.effect.concurrent.{ExitCode, SafeApp}
  * import cats.implicits._
  * 
  * object MyApp extends SafeApp[IO] {
  *   def run(args: List[String]): IO[ExitCode] =
  *     args.headOption match {
  *       case Some(name) =>
  *         IO(println("Hello, $name.")).as(ExitCode.Success)
  *       case None =>
  *         IO(System.err.println("Usage: MyApp name")).as(ExitCode(2))
  *     }
  * }
  * }}}
  * 
  * @tparam F the type of [[ConcurrentEffect]]
  */
abstract class SafeApp[F[_]](implicit F: ConcurrentEffect[F]) {
  /** Runs an app as a concurrent effect `F[_]`.
    * 
    * @return an [[ExitCode]] for the JVM to exit with
    */
  @JSExport
  def run(args: List[String]): F[ExitCode]

  /** Invokes [[run]] with the arguments, and exits with the effect's
    * returned exit code.
    */
  @JSExport
  final def main(args: Array[String]): Unit =
    SafeApp.runMain(args, run).runAsync {
      case Left(t) =>
        IO(Logger.reportFailure(t)) *> IO(sys.exit(ExitCode.Error.intValue))
      case Right(code) =>
        IO(sys.exit(code.intValue))
    }.unsafeRunSync()
}

private[concurrent] object SafeApp {
  /** The main logic behind [[SafeApp.main]].  Exposed so we can test
    * without shutting down, or using SecurityManager hacks.
    */
  def runMain[F[_]](args: Array[String], run: List[String] => F[ExitCode])(implicit F: ConcurrentEffect[F]): IO[ExitCode] = for {
    exitCodeDeferred <- Deferred[IO, ExitCode]
    _ <- F.runAsync(run(args.toList)) {
      case Left(t) =>
        IO(Logger.reportFailure(t)) *> exitCodeDeferred.complete(ExitCode.Error)
      case Right(code) =>
        exitCodeDeferred.complete(code)
    }
    exitCode <- exitCodeDeferred.get
  } yield exitCode
}
