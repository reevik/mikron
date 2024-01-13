# Mikron

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![License](https://img.shields.io/badge/JDK-21%20-green.svg)](https://github.com/reevik/darkest/wiki/Java-Support)
[![License](https://img.shields.io/badge/Release-0.1.0.Alpha%20-green.svg)](https://central.sonatype.com/artifact/net.reevik/darkest)
[![Javadoc](https://img.shields.io/badge/Javadoc%20-green.svg)](https://reevik.github.io/mikron/)


A minimalistic IoC container for dependency injection. Dependency injection is considered to be a feature in large enterprise frameworks like Spring. 

The maven-based project is still under development. If you want to try out, you can clone and build it from the source. 

```xml

<dependency>
  <groupId>net.reevik</groupId>
  <artifactId>mikron</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Usage

To initialize the Mikron context in your Java application, you will use Mikron annotations. In the following example, we activate the context and make the framework search for managed instances in the specified package:

```java
@ManagedApplication(packages = {"your.package.to.scan"})
public class Main {

  public static void main(String[] args) {
    // Start the Mikron context.
    MikronContext.init(Main.class);
  }
}
```

To define managed instances, you will use @Managed annotation:

```java
@Managed
public class ManagedObject {

  @Wire
  private ManagedDependency managedDependency;
  
```

and `@Wire` annotation to introduce dependency injection point.

## Bugs and Feedback

For bugs, questions and discussions please use
the [GitHub Issues](https://github.com/notingolmo/mikron/issues).

## LICENSE

Copyright 2024 Erhan Bagdemir

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[license]:LICENSE-2.0.txt
[license img]:https://img.shields.io/badge/License-Apache%202-blue.svg