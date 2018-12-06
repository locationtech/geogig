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

### 3. Create the batch file

- Open the Fortify "Scan Wizzard" application
- Select "Add Project Root" and browse to the `build/fortify/geogig-1.4-SNAPSHOT` directory generated at step 1.
- Click "Next" and under "Enable build integration" select the "Maven" checkbox.

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