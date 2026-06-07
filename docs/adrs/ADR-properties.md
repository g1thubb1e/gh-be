# BE -  환경변수 사용 방법

분야: 기술
상태: 작업중
작성자: 황정민

## 🎯 Context

<aside>
💡

- 환경변수 관리에는 여러 방법이 있음
- 관리 방식이 불일치할 경우 추후 타인의 코드를 볼 때 혼란을 야기할 수 있음
</aside>

## **🤝 Decision**

<aside>
💡

</aside>

---

### **📑 상세 규격**

- 기본적으로 필요한 모든 환경변수는 `application.properties`에 정의한다.
- 정의한 환경변수는 스프링 어플리케이션 내에서 Properties 클래스를 이용해 가져온다.
    - global 패키지 하위에 존재한다.

#### 환경변수 도메인 분류가 명확한 경우

- `{도메인명}Properties`에 두고 사용한다.

```java
package com.ssafy.githubble.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "github.oauth")
public class GithubOAuthProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "read:user user:email";
    private String authorizeUrl;
}
```

#### 환경변수 도메인이 명확하지 않은 경우

- `GlobalProperties`에 두고 사용한다.

```java
package com.ssafy.githubble.global.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "global")
public class GlobalProperties {
    private String feBaseUrl;
    private String feLoginSuccessUri;
    
    // 밑에 필요한 환경변수 추가
}

```

## **⚖️ Consequences**

### **👍 장점**

- **관리 효율:** 현재 사용중인 환경변수 명확하게 파악 가능

### **👎 단점**

- **작업량 증가**: 환경변수를 추가할 때 마다 작성해야 하는 코드 양이 는다.
- **복잡도 증가**: 환경변수 사용을 위해서 properties 클래스 의존성 주입을 받아야 한다.