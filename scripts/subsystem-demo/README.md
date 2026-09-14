# Increment 26 subsystem demonstration

Regenerates `../push-scan/sample-reports/spring-petclinic-subsystems-post-increment-26.json`:
spring-petclinic scanned with two declared subsystems, `owner` governed by
the strict matrix and `vet` treated as a legacy era that tolerates a
controller calling a repository directly.

It exists because `Main`'s illustrative config deliberately declares **no**
subsystems — so the committed default-path scan stays comparable with every
earlier increment — and OQ-04 still needs evidence that per-subsystem
matrices do something on real code.

`SubsystemDemo` sits in package `org.aerf.pipeline` because
`Main.illustrativeConfig` is package-private; widening production API for a
demonstration would be the wrong trade.

```sh
cd /path/to/aerf && mvn -q install -DskipTests
mvn -q -pl aerf-pipeline dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt

git clone --depth 1 https://github.com/spring-projects/spring-petclinic.git /tmp/spring-petclinic
cd /tmp/spring-petclinic && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/petclinic-cp.txt

CP="/path/to/aerf/aerf-pipeline/target/classes:$(cat /tmp/cp.txt)"
mkdir -p /tmp/demo/org/aerf/pipeline
cp scripts/subsystem-demo/SubsystemDemo.java /tmp/demo/org/aerf/pipeline/
javac -proc:none -cp "$CP" -d /tmp/demo-classes /tmp/demo/org/aerf/pipeline/SubsystemDemo.java

java -cp "/tmp/demo-classes:$CP" org.aerf.pipeline.SubsystemDemo \
  /tmp/spring-petclinic/src/main/java $(tr ':' ' ' < /tmp/petclinic-cp.txt)
```

Expected: layer entropy **11/21 = 0.5238**, against **14/21 = 0.6667** on the
default single-matrix path. The denominator stays at 21 — a subsystem
declaration changes which matrix judges an edge, never which edges are in
scope — and graph-wide confidence is unchanged, per Amendment 7.
