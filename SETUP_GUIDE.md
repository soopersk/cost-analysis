# Calculator Cost Analytics Dashboard - Setup Guide

## 🎯 Overview

This is a production-ready cost analytics dashboard for tracking Databricks calculator run costs. It consists of:

- **Frontend**: Modern React dashboard with beautiful visualizations
- **Backend**: Spring Boot REST API with PostgreSQL database
- **Data Pipeline**: Databricks notebook for cost calculation

## 📋 Prerequisites

- Java 17+
- Node.js 18+
- PostgreSQL 13+
- Databricks workspace with Unity Catalog
- Maven or Gradle

## 🚀 Quick Start

### 1. Database Setup

```sql
-- Create database
CREATE DATABASE observability_db;

-- Create schemas
CREATE SCHEMA IF NOT EXISTS finops;
CREATE SCHEMA IF NOT EXISTS observability;

-- Run the cost table creation script (from previous response)
-- See: calculator_run_costs table definition
```

### 2. Backend Setup (Spring Boot)

```bash
# Clone or create project
mkdir calculator-cost-analytics
cd calculator-cost-analytics

# Create Maven project structure
mvn archetype:generate \
  -DgroupId=com.company.observability \
  -DartifactId=cost-analytics \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false

# Add dependencies to pom.xml
```

**pom.xml**:
```xml
<dependencies>
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Boot Starter Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Spring Boot Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer for Prometheus -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>

    <!-- Spring Boot Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**File Structure**:
```
src/main/java/com/company/observability/
├── CostAnalyticsApplication.java
├── controller/
│   └── CostAnalyticsController.java
├── service/
│   └── CostAnalyticsService.java
├── repository/
│   └── CalculatorRunCostRepository.java
├── entity/
│   └── CalculatorRunCost.java
└── dto/
    └── CostAnalyticsDTOs.java

src/main/resources/
└── application.yml
```

**Main Application Class**:
```java
package com.company.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CostAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CostAnalyticsApplication.class, args);
    }
}
```

**Configure Database**:
```bash
# Set environment variables
export DB_USERNAME=observability_user
export DB_PASSWORD=your_secure_password

# Or use application-local.yml
```

**Run the application**:
```bash
mvn spring-boot:run
```

The API will be available at: `http://localhost:8080`

### 3. Frontend Setup (React)

```bash
# Create React app with Vite
npm create vite@latest calculator-cost-dashboard -- --template react
cd calculator-cost-dashboard

# Install dependencies
npm install recharts lucide-react
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

**Configure Tailwind** (`tailwind.config.js`):
```javascript
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}
```

**Add Tailwind directives** (`src/index.css`):
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

**Update main component** (`src/App.jsx`):
```javascript
import CalculatorCostDashboard from './components/CalculatorCostDashboard'

function App() {
  return <CalculatorCostDashboard />
}

export default App
```

**Add the dashboard component** (`src/components/CalculatorCostDashboard.jsx`):
- Copy the React component code provided earlier

**Configure API endpoint** (`src/config.js`):
```javascript
export const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
```

**Update to use real API** (in CalculatorCostDashboard.jsx):
```javascript
useEffect(() => {
  const fetchData = async () => {
    setLoading(true);
    try {
      const response = await fetch(
        `${API_BASE_URL}/api/v1/cost-analytics?period=${selectedPeriod}&calculator=${selectedCalculator}`
      );
      const data = await response.json();
      setData(data);
    } catch (error) {
      console.error('Error fetching cost data:', error);
    } finally {
      setLoading(false);
    }
  };
  
  fetchData();
}, [selectedPeriod, selectedCalculator]);
```

**Run the frontend**:
```bash
npm run dev
```

The dashboard will be available at: `http://localhost:5173`

## 🔄 Data Pipeline Setup

### Databricks Notebook

1. **Create notebook** in Databricks: `/Repos/cost-attribution/calculate_calculator_costs`
2. **Copy the Python code** from the earlier Databricks notebook response
3. **Create Databricks Job**:
   - Schedule: Daily @ 6 AM UTC
   - Cluster: Use job cluster (2-4 workers)
   - Parameters:
     - `target_date`: `{{ date_sub(current_date(), 1) }}`
     - `observability_api_url`: Your Spring Boot API URL
     - `observability_api_token`: API token (store in Databricks secrets)

4. **Set up Databricks secrets**:
```bash
# Create secret scope
databricks secrets create-scope --scope cost-attribution

# Add API token
databricks secrets put --scope cost-attribution --key observability-api-token
```

### Airflow DAG (Optional)

If using Airflow, create DAG: `dags/databricks_cost_attribution.py`
- Copy the Airflow DAG code from earlier response

## 📊 Dashboard Features

### Key Visualizations

1. **Total Cost Card** - Overall spend with trend
2. **Cost Trend Chart** - 30-day area chart showing DBU, VM, storage costs
3. **Cost Breakdown Pie Chart** - Component distribution
4. **Calculator Comparison** - Stacked bar chart by calculator
5. **Efficiency Metrics** - Progress bars for optimization opportunities
6. **Recent Runs Table** - Detailed run-level cost data

### Metrics Displayed

- Total cost (30-day rolling)
- Total runs executed
- Average cost per run
- Cluster utilization efficiency
- Spot instance adoption
- Photon usage
- Retry and failure rates

## 🔐 Security

### Backend Security

Add Spring Security dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

Configure JWT authentication:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/cost-analytics/**").authenticated()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
        return http.build();
    }
}
```

### Environment Variables

```bash
# Backend
export DB_USERNAME=observability_user
export DB_PASSWORD=secure_password
export JWT_SECRET=your_jwt_secret

# Frontend
export VITE_API_URL=https://api.your-company.com
export VITE_AUTH_ENABLED=true
```

## 🚢 Deployment

### Backend (Spring Boot)

**Docker**:
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/cost-analytics-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build and run**:
```bash
mvn clean package
docker build -t calculator-cost-analytics .
docker run -p 8080:8080 \
  -e DB_USERNAME=user \
  -e DB_PASSWORD=pass \
  calculator-cost-analytics
```

### Frontend (React)

**Build**:
```bash
npm run build
```

**Deploy to S3 + CloudFront**:
```bash
aws s3 sync dist/ s3://your-bucket-name/
aws cloudfront create-invalidation --distribution-id YOUR_ID --paths "/*"
```

**Or use Vercel/Netlify**:
```bash
npm install -g vercel
vercel --prod
```

## 📈 Monitoring

### Backend Metrics

Access Prometheus metrics at: `http://localhost:8080/actuator/prometheus`

**Key metrics**:
- `http_server_requests_seconds` - API latency
- `jvm_memory_used_bytes` - Memory usage
- `jdbc_connections_active` - DB connections

### Logging

Configure structured logging:
```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

## 🧪 Testing

### Backend Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class CostAnalyticsControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testGetDashboardData() throws Exception {
        mockMvc.perform(get("/api/v1/cost-analytics?period=30d"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.dailyCosts").isArray());
    }
}
```

### Frontend Tests

```bash
npm install -D @testing-library/react vitest
```

```javascript
import { render, screen } from '@testing-library/react';
import CalculatorCostDashboard from './CalculatorCostDashboard';

test('renders dashboard title', () => {
  render(<CalculatorCostDashboard />);
  expect(screen.getByText(/Calculator Cost Analytics/i)).toBeInTheDocument();
});
```

## 🎨 Customization

### Adding New Metrics

1. **Backend**: Add query to repository
2. **Backend**: Add DTO field
3. **Frontend**: Update chart component

### Changing Colors

Update color scheme in React component:
```javascript
const COLORS = {
  primary: '#3b82f6',    // Blue
  secondary: '#8b5cf6',  // Purple
  success: '#10b981',    // Green
  warning: '#f59e0b',    // Orange
};
```

## 📚 API Documentation

Generate OpenAPI docs:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

Access Swagger UI: `http://localhost:8080/swagger-ui.html`

## 🐛 Troubleshooting

### Common Issues

**CORS errors**:
- Check `application.yml` CORS settings
- Ensure frontend URL is in `allowed-origins`

**Database connection errors**:
- Verify PostgreSQL is running
- Check credentials in environment variables
- Ensure schema `finops` exists

**No data showing**:
- Check if Databricks job has run
- Verify API endpoint returns data: `curl http://localhost:8080/api/v1/cost-analytics`
- Check browser console for errors

## 📞 Support

For issues or questions:
- Check logs: `tail -f logs/application.log`
- Enable debug logging: `logging.level.com.company=DEBUG`
- Review Databricks job logs

## 🎉 Success!

You should now have a fully functional cost analytics dashboard! 

Access it at: `http://localhost:5173`

Monitor costs, identify optimization opportunities, and track efficiency metrics in real-time.
