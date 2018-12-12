# Running Fortify Security Scans


##Resouces:

-  [Fortify 17.20 user manuals](https://www.microfocus.com/documentation/fortify-static-code-analyzer-and-tools/1720/) 
-  [Fortify 18.20 user manuals](https://www.microfocus.com/documentation/fortify-static-code-analyzer-and-tools/1820/) 

## Quick start

### 1. Prepare code for analysis

Fortify doesn't understand Lombok annotations, so we've got a maven profile to generate a "delombok'ed" code base under `<geogig>/build/fortify/geogig-<version>`:

```
$ cd <geogig>/src
$ mvn clean install -DskipTests -Pfortify
$ cd ../build/fortify/geogig-1.4-SNAPSHOT/src/
$ mvn clean install
```
The path to `build/fortify/geogig-1.4-SNAPSHOT` is the one we'll give to Fortify for analysis.

### 2. Install the fortify maven plugin

Before running a scan, make sure to have the fortify maven plugin installed.

```
PS C:\Users\User>
PS C:\Users\User> Expand-Archive "C:\Program Files\HPE_Security\Fortify_SCA_and_Apps_17.20\plugins\maven\maven-plugin-bin.zip" fortify_maven_plugin
PS C:\Users\User> cd .\fortify_maven_plugin\
PS C:\Users\User> cd .\install.bat
```

That will install the maven plugin in the local maven repository at `%HOME%\.m2\repository\com\hpe\security\fortify\maven\plugin\sca-maven-plugin\17.20\sca-maven-plugin-17.20.jar`

### 3. Configure the fortify maven plugin

At the project's root pom:

```
      <build>
        <plugins>
          <plugin>
            <groupId>com.hpe.security.fortify.maven.plugin</groupId>
            <artifactId>sca-maven-plugin</artifactId>
            <version>17.20</version>
            <executions>
              <execution>
                <goals>
                  <goal>clean</goal>
                  <goal>translate</goal>
                  <goal>scan</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>
```

### Run the scan

We've added the `sca-maven-plugin` to a `fortify` maven profile on the delomboked source code base's root pom. So run:

```
PS C:\Users\User> cd <geogig>/buld/fortify/geogig-<version>/src
PS C:\Users\User> mvn clean integration-test -Pfortify
```

You'll see sections like the following for each project module as the `sca-maven-plugin` runs the `clean`, `translate`, and `scan` goals:

```
[INFO] --- sca-maven-plugin:17.20:clean (default) @ geogig-core ---
[INFO] Aggregate: true
[INFO] Index of Project: 3/13
[INFO] Packaging Type: jar
[INFO] Base Dir: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core
[INFO] POM: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\pom.xml
[INFO] Skipping to clean in aggregate mode
[INFO]
[INFO] --- maven-jar-plugin:3.0.2:jar (default-jar) @ geogig-core ---
...
[INFO] --- sca-maven-plugin:17.20:translate (default) @ geogig-core ---
[INFO] Aggregate: true
[INFO] Index of Project: 3/13
[INFO] Packaging Type: jar
[INFO] Base Dir: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core
[INFO] POM: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\pom.xml
[INFO] Fail on Error: false
[INFO] Translating pom.xml...
[INFO] Build ID: geogig-1.4-SNAPSHOT
[INFO] Executing Command: cmd.exe /X /C "sourceanalyzer @C:/Users/Developer/git/geogig/build/fortify/geogig-1.4-SNAPSHOT/src/core/target/fortify/sca-translate-geogig-core-pom.txt"

Fortify Static Code Analyzer 17.20.0183 (using JRE 1.8.0_144)
[INFO] Source File Path: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\src\main\java
[INFO] Resources: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\src\main\resources
[INFO] Translating main...
[INFO] Build ID: geogig-1.4-SNAPSHOT
[INFO] Source: 1.8
[INFO] Executing Command: cmd.exe /X /C "sourceanalyzer @C:/Users/Developer/git/geogig/build/fortify/geogig-1.4-SNAPSHOT/src/core/target/fortify/sca-translate-geogig-core-main.txt"
Fortify Static Code Analyzer 17.20.0183 (using JRE 1.8.0_144)
[INFO]
[INFO] --- sca-maven-plugin:17.20:scan (default) @ geogig-core ---
[INFO] Aggregate: true
[INFO] Index of Project: 3/13
[INFO] Packaging Type: jar
[INFO] Base Dir: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core
[INFO] POM: C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\pom.xml
[INFO] Skipping to scan in aggregate mode
[INFO]
```

Once the build finishes, the following log files will be available at the root's `target/fortify` folder:

```
$ ls -l target/fortify/
total 4989
-rw-r--r-- 1 Developer 197121 4891410 Dec 12 04:54 geogig-1.4-SNAPSHOT.fpr
-rw-r--r-- 1 Developer 197121     539 Dec 12 04:41 sca-clean.log
-rw-r--r-- 1 Developer 197121     156 Dec 12 04:41 sca-clean-geogig.txt
-rw-r--r-- 1 Developer 197121   14232 Dec 12 04:54 sca-scan.log
-rw-r--r-- 1 Developer 197121     389 Dec 12 04:44 sca-scan-geogig.txt
-rw-r--r-- 1 Developer 197121  188783 Dec 12 04:44 sca-translate.log
-rw-r--r-- 1 Developer 197121     435 Dec 12 04:41 sca-translate-geogig-main.txt
-rw-r--r-- 1 Developer 197121     332 Dec 12 04:41 sca-translate-geogig-pom.txt
```

The `.fpr` file is the Fortify project file you can open in the "Audit Workbench" Fortify application.

Take a look at `sca-translate.log`. Most probably, Fortify will log several translation errors as it can't understand some Java constructs or method calls. Look for `SEVERE` log messages, they will mostly be of the type `Unable ro resolve function ....`.

You need to resolve all of those because that means all those files and all its callers weren't analyzed, leading a lot of false positives and warnings when opening the `.fpr` in Audit Workbench.

Our first run resulted in 41 log entries like the following:

```
$ grep "Unable to resolve function" sca-translate.log
Unable to resolve function 'com.google.common.collect.Lists.transform' at (C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\api\src\main\java\org\locationtech\geogig\model\RevObjects.java:355:15)
Unable to resolve function 'com.google.common.collect.Iterables.transform' at (C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\api\src\main\java\org\locationtech\geogig\storage\IndexDuplicator.java:160:39)
Unable to resolve function 'com.google.common.collect.Iterators.transform' at (C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\api\src\main\java\org\locationtech\geogig\storage\internal\ObjectStoreDiffObjectIterator.java:141:26)
Unable to resolve function 'com.google.common.collect.Iterables.transform' at (C:\Users\Developer\git\geogig\build\fortify\geogig-1.4-SNAPSHOT\src\core\src\main\java\org\locationtech\geogig\data\FindFeatureTypeTrees.java:88:54)
...
```

In our case, most of these errors were resolved by replacing calls to guava utility classes by plain Java 8 constructs, mostly using the Streams API. 

For example:
```
Lists.transform(commit.getParentIds(), RevObjects::toShortString);
```
was replaced by
```
commit.getParentIds().stream().map(RevObjects::toShortString).collect(Collectors.toList())
```
---

That's it for the quick start guide. Follow on with the detailed process section bellow for more information.

---

## Detailed process 

![Install Fortify Maven plugin](/home/groldan/git/geogig/doc/fortify/00_install_fortify_maven_plugin.png  "Install Fortify Maven plugin")

## Prepare codebase for analysis

### Delombok

Our codebase makes use of Project Lombok[Project Lombok](https://projectlombok.org/features/delombok) 1.18.2 to reduce Java boilerplate code.

Lombok uses java annotations to add java code at compile time. For example, in the following code:

```
import lombok.Value;
public @Value class Point2D{
	private final double x;
	private final double y;
}
```

the `@Value` annotation generates required Java code for instances of that class to be an immutable value object with standard property accessors, effectively becoming something like

```
public class Point2D{
 	private final double x;
	private final double y;
	
	public @Generated Point2D(double x, double y){
		this.x = x;
		this.y = y;
	}
	public @Generated @Override String toString(){...}
	public @Generated @Override boolean equals(Object o){...}
	public @Generated @Override int hashCode(){...}
	public double getX(){return x;}
	public double getY(){return y;}
}
```

Being a compile time preprocessor, what it generates is not available in the source code files but in the compiled bytecode.

Fortify is a static _code_ analyzer and cannot interpret Lombok annotations. This leads to Fortify scans on any Lombok annotated class, or any client class of an annotated class, to be unparseable by Lombok and hence not scanned for security issues. 

Fortunately, Lombok comes with a [delombok](https://projectlombok.org/features/delombok) utility that creates a separate, pre-processed set of source code files with annotations removed and the source code that it adds at compile time written directly to the source code.

```
alias delombok="java -jar $HOME/.m2/repository/org/projectlombok/lombok/1.18.2/lombok-1.18.2.jar delombok"
```

```
<build>
  <plugins>
    <plugin>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok-maven-plugin</artifactId>
      <version>1.18.4.0</version>
      <executions>
        <execution>
          <phase>generate-sources</phase>
          <goals>
            <goal>delombok</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_01.png) 

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_02_add_project_root.png) 

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_03_maven_integration.png) 

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_04_check_enable_maven_integration.png) 

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_05_generate_batch_file.png) 

![](/home/groldan/git/geogig/doc/fortify/01_scan_wizzard_06_review_batch_file.png) 

![](/home/groldan/git/geogig/doc/fortify/02_run_scan_01.png) 

![](/home/groldan/git/geogig/doc/fortify/02_run_scan_02_running.png) 

![](/home/groldan/git/geogig/doc/fortify/02_run_scan_03_finished.png) 

![](/home/groldan/git/geogig/doc/fortify/03_audit_workbench_01_open.png) 

![](/home/groldan/git/geogig/doc/fortify/03_audit_workbench_02_open_project.png) 

![](/home/groldan/git/geogig/doc/fortify/03_audit_workbench_03_open_fpr_file.png) 

![](/home/groldan/git/geogig/doc/fortify/03_audit_workbench_04_summary.png) 


- Create and run .bat file
- Watch for warnings, they're actually fatal errors
- Analyze results
- Create filters