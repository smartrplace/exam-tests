# exam-tests
Example tests for OSGi and OGEMA with Pax Exam

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
