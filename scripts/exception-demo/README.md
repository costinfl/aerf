# Increment 28 approved-exception demonstration

Excuses the one genuine N+1 AERF has ever found on real code — legacy
spring-framework-petclinic's
`JdbcOwnerRepositoryImpl#loadOwnersPetsAndVisits → loadPetsAndVisits`,
an iterated persistence call — and shows that the measurement does not move.

It exists because `Main`'s illustrative config declares **no** exceptions, so
the committed default-path scans stay comparable across increments, and because
the claim "an approved exception changes no measured value" deserves proving on
real code rather than only on fixtures.

`ExceptionDemo` sits in package `org.aerf.pipeline` because
`Main.illustrativeConfig` is package-private; widening production API for a
demonstration would be the wrong trade.

```sh
cd /path/to/aerf && mvn -q install -DskipTests
mvn -q -pl aerf-pipeline dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt

git clone --depth 1 https://github.com/spring-petclinic/spring-framework-petclinic.git \
  /tmp/spring-framework-petclinic
cd /tmp/spring-framework-petclinic && ./mvnw -q dependency:build-classpath \
  -Dmdep.outputFile=/tmp/framework-petclinic-cp.txt

CP="/path/to/aerf/aerf-pipeline/target/classes:$(cat /tmp/cp.txt)"
mkdir -p /tmp/exdemo/org/aerf/pipeline
cp scripts/exception-demo/ExceptionDemo.java /tmp/exdemo/org/aerf/pipeline/
javac -proc:none -cp "$CP" -d /tmp/exdemo-classes /tmp/exdemo/org/aerf/pipeline/ExceptionDemo.java

java -cp "/tmp/exdemo-classes:$CP" org.aerf.pipeline.ExceptionDemo \
  /tmp/spring-framework-petclinic/src/main/java $(tr ':' ' ' < /tmp/framework-petclinic-cp.txt)
```

Observed:

```
BEFORE  E_P=0.125 weighted=0.125 relevant=8 flagged=1 total=0.041666666666666664
AFTER   E_P=0.125 weighted=0.125 relevant=8 flagged=1 total=0.041666666666666664
FINDING STILL LISTED: true
LEDGER  persistence excused by alice - batched at the JDBC layer; reviewed 2026-09
UNMATCHED 0
```

Every measured value is identical, the flagged edge list is equal to the
unexcused run's, and what changed is only the governance verdict: the ledger now
names who accepted the finding and why. No sample report is committed for this
run — the numbers above are the evidence, and the two full scans would differ
only in the `exceptionLedger` key.
