import groovy.json.JsonSlurper

val matrixFile = settingsDir.resolve("release/release-matrix.json")
check(matrixFile.isFile) { "Missing central release matrix: $matrixFile" }
val matrix = JsonSlurper().parse(matrixFile) as? Map<*, *>
    ?: error("Central release matrix root must be an object: $matrixFile")
val artifacts = (matrix["artifacts"] as? List<*>)
    ?.map { artifact -> artifact as? Map<*, *> ?: error("Invalid artifact row in $matrixFile") }
    ?: error("Missing artifact inventory in $matrixFile")
val runtimes = (matrix["runtimes"] as? List<*>)
    ?.map { runtime -> runtime as? Map<*, *> ?: error("Invalid runtime row in $matrixFile") }
    ?: error("Missing runtime inventory in $matrixFile")

gradle.extensions.extraProperties.apply {
    set("etherologyReleaseMatrixFile", matrixFile)
    set("etherologyReleaseMatrix", matrix)
    set("etherologyReleaseArtifacts", artifacts)
    set("etherologyReleaseRuntimes", runtimes)
}
