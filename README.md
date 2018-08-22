# exam-tests
Example tests for OSGi and OGEMA with Pax Exam. They show in particular how to configure tests such that 

* they run with either the forked Pax Exam container or the native Pax Exam container
* they run with different OSGi framework bundles (Equinox and Felix)
* they run with different Java versions (<9 and >=9)
* they run with Java/OSGi/OGEMA security enabled

## Run tests
In a shell navigate to the target project and execute
```
mvn test
```
Once all dependencies of the project are resolved you may want to run the tests in offline mode:
```
mvn test -o
```
To execute only a specific test class:
```
mvn test -o -Dtest=FelixNativeTest
```
and to execute only a specific test method within a specific class:
```
mvn test -o -Dtest=FelixNativeTest#startupWorks
```
