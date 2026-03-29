# Cross-Language LSP Test Plan for karellen-jdtls-kotlin

**Target project:** headout/absolut (`/home/arcivanov/Documents/src/headout/absolut`)

This test plan covers all cross-language LSP features in both directions
(Java-to-Kotlin and Kotlin-to-Java) using real files from the absolut codebase.
All line/character positions are **1-based** per LSP convention.

---

## Table of Contents

1. [Hover](#1-hover)
2. [Go-to-Definition](#2-go-to-definition)
3. [Find References](#3-find-references)
4. [Call Hierarchy](#4-call-hierarchy)
5. [Type Hierarchy](#5-type-hierarchy)
6. [Document Symbols](#6-document-symbols)
7. [Code Lens](#7-code-lens)

---

## 1. Hover

### 1.1 Kotlin hovering over Java type (cross-module import)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 7, **Character:** 40
- **Symbol:** `AriesUserRole` in `import tourlandish.common.security.AriesUserRole`
- **Category:** Hover / Kotlin-to-Java / Type
- **Expected:** Hover shows `public enum AriesUserRole implements PermissionBasedRole` with Javadoc "Aries User Role"

### 1.2 Kotlin hovering over Java static method call

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 48, **Character:** 36
- **Symbol:** `rolesToAuth` in `SecurityConstant.rolesToAuth(grantedRoleSet)`
- **Category:** Hover / Kotlin-to-Java / Method
- **Expected:** Hover shows `public static List<SimpleGrantedAuthority> rolesToAuth(Set<AriesUserRole> roles)` with Javadoc "This Method Converts Role Set to List of SimpleGrantedAuth"

### 1.3 Kotlin hovering over Java class used as supertype

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Line:** 3, **Character:** 40
- **Symbol:** `AriesCommonException` in `import tourlandish.aries.exception.AriesCommonException`
- **Category:** Hover / Kotlin-to-Java / Type
- **Expected:** Hover shows `public class AriesCommonException extends AbstractApplicationException` with Javadoc about generic calipso service exception

### 1.4 Kotlin hovering over Java exception type in catch block

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.calipso/src/main/kotlin/tourlandish/calipso/service/bnpl/BnplMITPaymentService.kt`
- **Line:** 212, **Character:** 23
- **Symbol:** `PaymentCommonException` in `throw PaymentCommonException(...)`
- **Category:** Hover / Kotlin-to-Java / Type
- **Expected:** Hover shows `public class PaymentCommonException extends AbstractApplicationException`

### 1.5 Java hovering over Kotlin class import

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 16, **Character:** 33
- **Symbol:** `AuthUtils` in `import tourlandish.aries.utils.AuthUtils;`
- **Category:** Hover / Java-to-Kotlin / Type
- **Expected:** Hover shows type info for `AuthUtils` class. **RESOLVED:** jdtls hover for Java-to-Kotlin types depends on KotlinSearchParticipant `locateMatches()` returning a proper KotlinElement; hover content may be limited to the simple name without Javadoc equivalent.

### 1.6 Java hovering over Kotlin facade class method call

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common.db/src/main/java/tourlandish/common/db/oberon/data/helpers/PriceProfileRelationshipValidator.java`
- **Line:** 60, **Character:** 63
- **Symbol:** `getBIG_DECIMAL_HUNDRED` in `NumberExtensionsKt.getBIG_DECIMAL_HUNDRED()`
- **Category:** Hover / Java-to-Kotlin / Facade method
- **Expected:** Hover shows the JVM accessor method signature. **RESOLVED:** Hover on facade class methods may not resolve to Kotlin source since `NumberExtensionsKt` is a JVM-generated facade; hover depends on whether the facade TYPE_DECL and METHOD_DECL are indexed correctly.

### 1.7 Java hovering over Kotlin field access

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 49, **Character:** 29
- **Symbol:** `authUtils` (field of type `AuthUtils`)
- **Category:** Hover / Java-to-Kotlin / Field type
- **Expected:** Hover shows `AuthUtils` as the declared type (standard Java hover, type resolved via index)

---

## 2. Go-to-Definition

### 2.1 Kotlin navigating to Java enum definition

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 47, **Character:** 54
- **Symbol:** `AriesUserRole` in parameter type `Set<AriesUserRole>`
- **Category:** Go-to-Definition / Kotlin-to-Java / Type
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common/src/main/java/tourlandish/common/security/AriesUserRole.java`, line 13, character 14 (`public enum AriesUserRole`)

### 2.2 Kotlin navigating to Java class via import

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 6, **Character:** 37
- **Symbol:** `SecurityConstant` in `import tourlandish.aries.security.SecurityConstant`
- **Category:** Go-to-Definition / Kotlin-to-Java / Import
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/security/SecurityConstant.java`, line 19, character 14 (`public class SecurityConstant`)

### 2.3 Kotlin navigating to Java static method

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 48, **Character:** 36
- **Symbol:** `rolesToAuth` in `SecurityConstant.rolesToAuth(grantedRoleSet)`
- **Category:** Go-to-Definition / Kotlin-to-Java / Method
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/security/SecurityConstant.java`, line 38, character 46 (`rolesToAuth` method declaration)

### 2.4 Kotlin navigating to Java superclass

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Line:** 5, **Character:** 31
- **Symbol:** `AriesCommonException` in `open class AmountException : AriesCommonException`
- **Category:** Go-to-Definition / Kotlin-to-Java / Supertype
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/exception/AriesCommonException.java`, line 13, character 14

### 2.5 Kotlin navigating to Java exception constructor

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.calipso/src/main/kotlin/tourlandish/calipso/service/bnpl/BnplMITPaymentService.kt`
- **Line:** 212, **Character:** 23
- **Symbol:** `PaymentCommonException` in `throw PaymentCommonException("Invalid payment charge status: ${charge.status}")`
- **Category:** Go-to-Definition / Kotlin-to-Java / Constructor
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/headout.feature.payment/src/main/java/headout/feature/payment/exception/PaymentCommonException.java`, line 6, character 14

### 2.6 Java navigating to Kotlin class definition

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 16, **Character:** 33
- **Symbol:** `AuthUtils` in `import tourlandish.aries.utils.AuthUtils;`
- **Category:** Go-to-Definition / Java-to-Kotlin / Type
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`, line 13, character 7 (`class AuthUtils`)

### 2.7 Java navigating to Kotlin facade class (top-level functions)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common.db/src/main/java/tourlandish/common/db/oberon/data/helpers/PriceProfileRelationshipValidator.java`
- **Line:** 3, **Character:** 50
- **Symbol:** `NumberExtensionsKt` in `import headout.feature.pricing.extensions.NumberExtensionsKt;`
- **Category:** Go-to-Definition / Java-to-Kotlin / Facade class
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`, line 1, character 1. The facade class `NumberExtensionsKt` maps to the file itself.

### 2.8 Java navigating to Kotlin method via Kotlin property getter (facade)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common.db/src/main/java/tourlandish/common/db/oberon/data/helpers/PriceProfileRelationshipValidator.java`
- **Line:** 60, **Character:** 63
- **Symbol:** `getBIG_DECIMAL_HUNDRED` in `NumberExtensionsKt.getBIG_DECIMAL_HUNDRED()`
- **Category:** Go-to-Definition / Java-to-Kotlin / Property getter
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`, line 1, character 1. This exercises the property/getter interop: Java `getBIG_DECIMAL_HUNDRED()` maps to Kotlin `val BIG_DECIMAL_HUNDRED`.

### 2.9 Java navigating to Kotlin class method call

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 98, **Character:** 26
- **Symbol:** `getLoginPageRedirectUrl` in `authUtils.getLoginPageRedirectUrl(...)`
- **Category:** Go-to-Definition / Java-to-Kotlin / Method
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`, line 32, character 9 (`fun getLoginPageRedirectUrl`)

### 2.10 Java navigating to Kotlin property (as getter)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 98, **Character:** 60
- **Symbol:** `getDefaultLoginRedirectUrl` in `authUtils.getDefaultLoginRedirectUrl()`
- **Category:** Go-to-Definition / Java-to-Kotlin / Property getter
- **Expected:** Navigates to `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`, line 20, character 5 (`val defaultLoginRedirectUrl`). This is a Kotlin `val` property; Java sees it as `getDefaultLoginRedirectUrl()`.

---

## 3. Find References

### 3.1 Find references to Java enum from Kotlin (without declarations)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common/src/main/java/tourlandish/common/security/AriesUserRole.java`
- **Line:** 13, **Character:** 14
- **Symbol:** `AriesUserRole`
- **Category:** Find References / Java type from Kotlin / includeDeclaration=false
- **Expected:** Results include Kotlin files that reference `AriesUserRole`:
  - `AuthUtils.kt` line 7 (import) and line 47 (parameter type)
  - `CookieSessionAuthenticationProvider.kt` (import/usage)
  - `HeadoutAuthTokenAuthenticationFilter.kt` (import/usage)
  - `HeadoutAuthBypassFilter.kt` (import/usage)
  - `AriesUserService.kt` (import/usage)
  - Plus Java references (SecurityConstant.java line 9, line 38 parameter, etc.)

### 3.2 Find references to Java enum from Kotlin (with declarations)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.common/src/main/java/tourlandish/common/security/AriesUserRole.java`
- **Line:** 13, **Character:** 14
- **Symbol:** `AriesUserRole`
- **Category:** Find References / Java type from Kotlin / includeDeclaration=true
- **Expected:** Same as 3.1 plus the declaration itself at AriesUserRole.java:13. Uses OrPattern internally.

### 3.3 Find references to Java static method from Kotlin

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/security/SecurityConstant.java`
- **Line:** 38, **Character:** 46
- **Symbol:** `rolesToAuth`
- **Category:** Find References / Java method from Kotlin / includeDeclaration=false
- **Expected:** Results include:
  - `AuthUtils.kt` line 48 (`SecurityConstant.rolesToAuth(grantedRoleSet)`)
  - Multiple `.kt` files that call `rolesToAuth` (HeadoutAuthBypassFilter.kt, HeadoutAuthTokenAuthenticationFilter.kt, etc.)
  - `UserAuthenticationProvider.java` (Java reference)

### 3.4 Find references to Java exception class from Kotlin subtypes

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/exception/AriesCommonException.java`
- **Line:** 13, **Character:** 14
- **Symbol:** `AriesCommonException`
- **Category:** Find References / Java type from Kotlin / includeDeclaration=false
- **Expected:** Results include Kotlin files referencing `AriesCommonException`:
  - `AmountException.kt` line 3 (import) and line 5 (supertype reference)
  - `AriesRefundException.kt` line 3 (import) and line 7 (supertype reference)

### 3.5 Find references to Kotlin class from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 13, **Character:** 7
- **Symbol:** `AuthUtils`
- **Category:** Find References / Kotlin type from Java / includeDeclaration=false
- **Expected:** Results include Java files referencing `AuthUtils`:
  - `ViewController.java` line 16 (import) and line 49 (field declaration)
  - Plus Kotlin files referencing `AuthUtils` (e.g., AriesConfiguration.kt)

### 3.6 Find references to Kotlin top-level property from Java (facade getter)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`
- **Line:** 12, **Character:** 5
- **Symbol:** `BIG_DECIMAL_HUNDRED`
- **Category:** Find References / Kotlin property from Java / facade
- **Expected:** Results include Java files calling `NumberExtensionsKt.getBIG_DECIMAL_HUNDRED()`:
  - `PriceProfileRelationshipValidator.java` lines 60, 66, 70, 74
  - `PriceProfileEvaluationHelper.java` (multiple usages)
  - `TourExtraChargeService.java` (usages)
  - Plus Kotlin files using `BIG_DECIMAL_HUNDRED` directly

### 3.7 Find references to Kotlin method from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 32, **Character:** 9
- **Symbol:** `getLoginPageRedirectUrl`
- **Category:** Find References / Kotlin method from Java / includeDeclaration=false
- **Expected:** Results include:
  - `ViewController.java` line 98 (`authUtils.getLoginPageRedirectUrl(...)`)
  - `ViewController.java` line 202 (second call site)
  - `ViewController.java` line 229 (third call site)

---

## 4. Call Hierarchy

### 4.1 Outgoing calls from Kotlin method to Java static method

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 47, **Character:** 9
- **Symbol:** `getGrantedAuthorities` method
- **Category:** Call Hierarchy / Outgoing / Kotlin-to-Java
- **Expected:** Outgoing calls include `SecurityConstant.rolesToAuth()` (Java static method at SecurityConstant.java:38)

### 4.2 Incoming calls to Java static method from Kotlin

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/security/SecurityConstant.java`
- **Line:** 38, **Character:** 46
- **Symbol:** `rolesToAuth` method
- **Category:** Call Hierarchy / Incoming / Java from Kotlin
- **Expected:** Incoming callers include:
  - `AuthUtils.getGrantedAuthorities()` at AuthUtils.kt:48
  - Kotlin callers in HeadoutAuthBypassFilter.kt, HeadoutAuthTokenAuthenticationFilter.kt, etc.

### 4.3 Incoming calls to Kotlin method from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 32, **Character:** 9
- **Symbol:** `getLoginPageRedirectUrl` method
- **Category:** Call Hierarchy / Incoming / Kotlin from Java
- **Expected:** Incoming callers include `ViewController.index()` (ViewController.java:98), `ViewController.mediaServiceRedirection()` (ViewController.java:202), and `ViewController.selfServeRedirection()` (ViewController.java:229). **RESOLVED:** Incoming calls TO Kotlin methods from Java depend on jdtls using `SearchEngine.getSearchParticipants()` in CallHierarchyCore. The jdtls fork (PR #3732) wires this for references but call hierarchy in jdt.ui (PR #2881) is needed for full support. If PR #2881 is not merged, incoming calls to Kotlin methods may be empty.

### 4.4 Outgoing calls from Java to Kotlin method

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/controllers/ViewController.java`
- **Line:** 89, **Character:** 16
- **Symbol:** `index` method
- **Category:** Call Hierarchy / Outgoing / Java-to-Kotlin
- **Expected:** Outgoing calls include `authUtils.getLoginPageRedirectUrl()` and `authUtils.getDefaultLoginRedirectUrl()` (both Kotlin targets). **RESOLVED:** Outgoing calls FROM Java to Kotlin require `CalleeMethodWrapper` fallback to `SearchParticipant.locateCallees()` (PR #2881). Without it, only Java callees appear.

### 4.5 Outgoing calls from Kotlin to Java exception constructor

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.calipso/src/main/kotlin/tourlandish/calipso/service/bnpl/BnplMITPaymentService.kt`
- **Line:** 98, **Character:** 9
- **Symbol:** `processBnplCharge` method
- **Category:** Call Hierarchy / Outgoing / Kotlin-to-Java
- **Expected:** Outgoing calls include `PaymentCommonException(String)` constructor at line 212. The outgoing call tree should show the Java exception class constructor.

### 4.6 Incoming calls to Kotlin facade method from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`
- **Line:** 12, **Character:** 5
- **Symbol:** `BIG_DECIMAL_HUNDRED` property
- **Category:** Call Hierarchy / Incoming / Kotlin facade from Java
- **Expected:** **RESOLVED:** Call hierarchy for Kotlin top-level properties (facade methods) may not resolve correctly. The property `BIG_DECIMAL_HUNDRED` is accessed from Java as `NumberExtensionsKt.getBIG_DECIMAL_HUNDRED()`, which requires getter/property interop in the call hierarchy provider.

---

## 5. Type Hierarchy

### 5.1 Subtypes of Java class including Kotlin subtypes

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/exception/AriesCommonException.java`
- **Line:** 13, **Character:** 14
- **Symbol:** `AriesCommonException`
- **Category:** Type Hierarchy / Subtypes / Java with Kotlin subtypes
- **Expected:** Subtypes include:
  - `AmountException` (Kotlin, AmountException.kt:5) and its nested `CurrencyMismatchException`
  - `AriesRefundException` (Kotlin, AriesRefundException.kt:7) and its nested classes (RefundReasonNotFoundException, RefundAmountCalculationException, etc.)
  - Any Java subtypes of AriesCommonException

### 5.2 Supertypes of Kotlin class reaching into Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Line:** 5, **Character:** 12
- **Symbol:** `AmountException`
- **Category:** Type Hierarchy / Supertypes / Kotlin-to-Java
- **Expected:** Supertype chain: `AmountException` -> `AriesCommonException` (Java) -> `AbstractApplicationException` (Java) -> ... -> `java.lang.Exception` -> `java.lang.Throwable` -> `java.lang.Object`
- **RESOLVED:** Type hierarchy for Kotlin types depends on `codeSelect()` returning a valid IJavaElement for the Kotlin class. KotlinSearchParticipant indexes SUPER_REF for Kotlin classes, but JDT's type hierarchy provider may not fully traverse the Kotlin-to-Java supertype chain since the Kotlin class is not a real IType from the Java model.

### 5.3 Supertypes of Kotlin nested class

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Line:** 14, **Character:** 11
- **Symbol:** `CurrencyMismatchException`
- **Category:** Type Hierarchy / Supertypes / Kotlin nested class
- **Expected:** Supertype chain: `CurrencyMismatchException` -> `AmountException` (Kotlin) -> `AriesCommonException` (Java)
- **RESOLVED:** Same limitation as 5.2 for Kotlin-initiated type hierarchies.

### 5.4 Subtypes of Kotlin class

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Line:** 5, **Character:** 12
- **Symbol:** `AmountException`
- **Category:** Type Hierarchy / Subtypes / Kotlin parent
- **Expected:** Subtypes include `CurrencyMismatchException` (nested at line 14)
- **RESOLVED:** Subtypes of Kotlin classes are a known limitation. JDT's type hierarchy engine builds hierarchies from the Java model; Kotlin types are not part of the Java model, so JDT cannot enumerate their subtypes natively. The KotlinSearchParticipant indexes SUPER_REF but JDT's `TypeHierarchy.getAllSubtypes()` does not query contributed participants for hierarchy construction.

### 5.5 Supertypes of Kotlin class with complex hierarchy (AriesRefundException)

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/service/exception/AriesRefundException.kt`
- **Line:** 7, **Character:** 12
- **Symbol:** `AriesRefundException`
- **Category:** Type Hierarchy / Supertypes / Kotlin-to-Java deep chain
- **Expected:** `AriesRefundException` -> `AriesCommonException` (Java) -> `AbstractApplicationException` (Java)
- **RESOLVED:** Same as 5.2.

---

## 6. Document Symbols

### 6.1 Document symbols for Kotlin file with class and methods

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Category:** Document Symbols / Kotlin
- **Expected:** Symbols include:
  - `AuthUtils` (Class, line 13)
  - `loginPageUrl` (Property/Field, line 16)
  - `logoutPageUrl` (Property/Field, line 17)
  - `orySessionCookiePrefix` (Property/Field, line 18)
  - `defaultLoginRedirectUrl` (Property/Field, line 20)
  - `defaultLogoutRedirectUrl` (Property/Field, line 21)
  - `getLoginPageUrl` (Method, line 24)
  - `getLogoutPageRedirectTo` (Method, line 28)
  - `getLoginPageRedirectUrl` (Method, line 32)
  - `extractOryCookieToken` (Method, line 36)
  - `getSessionCookie` (Method, line 42)
  - `getGrantedAuthorities` (Method, line 47)

### 6.2 Document symbols for Kotlin file with inheritance and nested classes

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/exceptions/AmountException.kt`
- **Category:** Document Symbols / Kotlin / Nested classes
- **Expected:** Symbols include:
  - `AmountException` (Class, line 5)
  - `CurrencyMismatchException` (Class, nested under AmountException, line 14)
  - Constructors for each class

### 6.3 Document symbols for Kotlin file with top-level functions and properties

- **File:** `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`
- **Category:** Document Symbols / Kotlin / Top-level declarations
- **Expected:** Symbols include:
  - `NumberExtensionsKt` (facade class, implicit)
  - `CURRENCY_PERC_SCALE` (Constant/Field, line 10)
  - `BIG_DECIMAL_HUNDRED` (Property/Field, line 12)
  - `bd` (Function/Method, line 16 - Int extension)
  - `BD` (Property, line 17 - Int extension property)
  - `bd` (Function/Method, line 19 - Double extension)
  - `scaledHalfEven` (Function/Method, line 21)
  - `safeDivideBy` (Function/Method, line 23)
  - `plusIf` (Function/Method, line 36)

### 6.4 Document symbols for Kotlin file with complex service class

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.calipso/src/main/kotlin/tourlandish/calipso/service/bnpl/BnplMITPaymentService.kt`
- **Category:** Document Symbols / Kotlin / Large class
- **Expected:** Symbols include:
  - `BnplMITPaymentService` (Class, line 58)
  - `processBnplCharge` (Method, line 98)
  - `handleSuccessfulCharge` (Method, line 262)
  - `handleFailureAndMaybeCancel` (Method, line 282)
  - `failItineraryPayLaterSchedule` (Method, line 348)
  - `handleChargeProcessingFailure` (Method, line 360)
  - `cancelItinerary` (Method, line 383)
  - `createSMPaymentRequestForBnpl` (Method, line 419)
  - `getPaymentRequestMIT` (Method, line 495)
  - `getItineraryPayLaterAttemptType` (Method, line 542)

### 6.5 Document symbols for Kotlin file with deeply nested exception hierarchy

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/service/exception/AriesRefundException.kt`
- **Category:** Document Symbols / Kotlin / Many nested classes
- **Expected:** Symbols include:
  - `AriesRefundException` (Class, line 7)
  - `RefundReasonNotFoundException` (Class, nested, line 16)
  - `RefundAmountCalculationException` (Class, nested, line 28)
  - `RefundAmountOverflowException` (Class, nested, line 40)
  - `InvalidRefundAmountException` (Class, nested, line 52)
  - `MaxRefundPercentageExceededException` (Class, nested, line 62)
  - `CurrencyMismatchException` (Class, nested, line 72)
  - `RefundToWalletNotAllowedException` (Class, nested, line 82)
  - `BookingNotFoundException` (Class, nested, line 92)
  - `SPApprovedAmountNotProvidedForNoChargeLossRefundException` (Class, nested, line 102)

---

## 7. Code Lens

### 7.1 Code lens on Kotlin class showing Java references

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 13
- **Symbol:** `AuthUtils` class declaration
- **Category:** Code Lens / Kotlin / References from Java
- **Expected:** Code lens shows reference count including Java files (ViewController.java imports and uses AuthUtils). The count should include both Java and Kotlin references.

### 7.2 Code lens on Kotlin method called from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/kotlin/tourlandish/aries/utils/AuthUtils.kt`
- **Line:** 32
- **Symbol:** `getLoginPageRedirectUrl` method declaration
- **Category:** Code Lens / Kotlin / Method references from Java
- **Expected:** Code lens shows reference count including the 3 call sites in ViewController.java (lines 98, 202, 229).

### 7.3 Code lens on Java class with Kotlin subtypes

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/exception/AriesCommonException.java`
- **Line:** 13
- **Symbol:** `AriesCommonException` class declaration
- **Category:** Code Lens / Java / References and implementations from Kotlin
- **Expected:** Code lens reference count includes Kotlin references (AmountException.kt, AriesRefundException.kt imports and supertype references). Implementation count should include Kotlin subtypes.

### 7.4 Code lens on Java static method called from Kotlin

- **File:** `/home/arcivanov/Documents/src/headout/absolut/tourlandish.aries/src/main/java/tourlandish/aries/security/SecurityConstant.java`
- **Line:** 38
- **Symbol:** `rolesToAuth` method declaration
- **Category:** Code Lens / Java / Method references from Kotlin
- **Expected:** Code lens shows reference count including Kotlin callers (AuthUtils.kt line 48 and others).

### 7.5 Code lens on Kotlin top-level property referenced from Java

- **File:** `/home/arcivanov/Documents/src/headout/absolut/headout.feature.pricing/src/main/kotlin/headout/feature/pricing/extensions/NumberExtensions.kt`
- **Line:** 12
- **Symbol:** `BIG_DECIMAL_HUNDRED` property
- **Category:** Code Lens / Kotlin / Facade property from Java
- **Expected:** Code lens shows reference count including Java files calling `NumberExtensionsKt.getBIG_DECIMAL_HUNDRED()` (PriceProfileRelationshipValidator.java has 4 call sites, plus PriceProfileEvaluationHelper.java and TourExtraChargeService.java).

---

## Summary — All Gaps Resolved (2026-03-29)

All previously documented gaps (G1–G8) have been resolved:

| ID | Feature | Resolution |
|----|---------|------------|
| G1 | Hover Java→Kotlin | Resolved: `HoverInfoProvider` uses `SearchParticipantRegistry.getLanguageId()` for correct language tag |
| G2 | Hover facade | Resolved: `resolveViaSearchParticipants()` in `JDTUtils.findElementsAtSelection()` |
| G3 | Call Hierarchy incoming Kotlin←Java | Resolved: `CallerMethodWrapper` accepts `A_INACCURATE` matches for contributed elements |
| G4 | Call Hierarchy outgoing Java→Kotlin | Resolved: `CalleeAnalyzerVisitor.resolveViaSearch()` + `CallHierarchyCore` `.kt` guard |
| G5 | Call Hierarchy facade | Resolved: `CalleeMethodWrapper` falls back to `SearchParticipant.locateCallees()` |
| G6 | Type Hierarchy supertypes Kotlin→Java | Resolved: `TypeHierarchyHandler.resolveContributedSupertypes()` reads `getSuperclassName()` and resolves via type search |
| G7 | Type Hierarchy subtypes Kotlin parent | Resolved: `supplementWithContributedSubtypes()` via IMPLEMENTORS search |
| G8 | Type Hierarchy subtypes Java←Kotlin | Resolved: same as G7 |

### Open Issues
- **[#26](https://github.com/karellen/karellen-jdtls-kotlin/issues/26) — Callee stub resolution**: Outgoing call hierarchy for Kotlin methods
  produces false positives for unresolved callees (e.g., `ConstraintViolations.get` instead of
  `Map.get`). Unresolved `CalleeStub` elements also cause slow project-wide searches in
  `CalleeMethodWrapper.resolveCallee()`. Remaining gaps: class properties/fields, implicit
  `this`, Kotlin stdlib extension functions, chained method calls.
- **[#25](https://github.com/karellen/karellen-jdtls-kotlin/issues/25) — Assignment-based type narrowing**: `val x = someMethod()` doesn't
  narrow `x`'s type from the return type of `someMethod()`. Affects callee resolution
  accuracy for variables whose type comes from assignment rather than annotation.
