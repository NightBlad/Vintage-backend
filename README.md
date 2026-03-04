# 🏥 Vintage Pharmacy

**Hệ thống bán dược phẩm chức năng** | Functional Pharmacy Management System

A comprehensive web-based pharmacy management system built with Spring Boot, featuring product management, user authentication, role-based access control, and AI-powered features through Gemini API integration.

## 📋 Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Database Configuration](#database-configuration)
- [Running the Application](#running-the-application)
- [Docker Deployment](#docker-deployment)
- [Security](#security)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [CI/CD Pipeline](#cicd-pipeline)
- [Contributing](#contributing)

## ✨ Features

- 🔐 **User Authentication & Authorization** with Spring Security
- 👥 **Role-Based Access Control** (Admin, Staff, Customer)
- 💊 **Product Management** (CRUD operations for pharmaceutical products)
- 🛒 **Shopping Cart & Order Processing**
- 📊 **Inventory Management**
- 🤖 **AI Integration** with Gemini API for intelligent features
- 🎨 **Responsive UI** with Bootstrap 5 and Thymeleaf
- 🔒 **CSRF Protection** and Security Headers
- 📦 **Multiple Database Support** (H2, MySQL, SQL Server)
- 🐳 **Docker Ready** with Docker Compose support
- 🚀 **CI/CD Pipeline** with Jenkins

## 🛠 Technology Stack

### Backend
- **Spring Boot** 3.2.0
- **Java** 17
- **Spring Data JPA** - Database operations
- **Spring Security** - Authentication & Authorization
- **Spring WebFlux** - Reactive HTTP client for Gemini API
- **Thymeleaf** - Server-side template engine
- **Bean Validation** - Input validation

### Frontend
- **Bootstrap** 5.3.0
- **jQuery** 3.7.0
- **Thymeleaf** with Spring Security integration

### Database
- **H2** 2.2.224 (Development)
- **MySQL** 8.2.0 (Production)
- **SQL Server** (Alternative production database)

### DevOps
- **Docker** & Docker Compose
- **Jenkins** - CI/CD automation
- **Maven** - Build tool

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Java 17** or higher
- **Maven** 3.6+ (or use included Maven Wrapper)
- **Docker** & Docker Compose (for containerized deployment)
- **MySQL** 8.0+ or **SQL Server** (for production database)

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/DaoTienDat2304/Vintage.git
cd Vintage
```

### 2. Build the Project

Using Maven Wrapper (recommended):
```bash
./mvnw clean install
```

Or using system Maven:
```bash
mvn clean install
```

## 🗄 Database Configuration

### Using H2 (Development)

H2 database is configured by default for development. No additional setup required.

**Access H2 Console:**
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:vintagedb`
- Username: `sa`
- Password: (leave empty)

### Using MySQL (Production)

1. Create a MySQL database:
```sql
CREATE DATABASE vintage_pharmacy;
```

2. Update `application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/vintage_pharmacy
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

### Using SQL Server (Production)

Update `application.properties`:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=vintage_pharmacy
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### Reset Admin Account

To reset the admin account, run the SQL script:
```bash
mysql -u username -p vintage_pharmacy < reset_admin.sql
```

## ▶️ Running the Application

### Local Development

```bash
./mvnw spring-boot:run
```

The application will be available at: `http://localhost:8080`

### Production Build

```bash
./mvnw clean package
java -jar target/vintage-0.0.1-SNAPSHOT.jar
```

## 🐳 Docker Deployment

### Using Docker Compose

```bash
docker-compose up -d
```

This will start:
- Spring Boot application
- MySQL database (if configured)
- Any additional services defined in `docker-compose.yml`

### Building Docker Image

```bash
docker build -t vintage-pharmacy .
```

### Running Docker Container

```bash
docker run -p 8080:8080 vintage-pharmacy
```

## 🔒 Security

### Authentication

The application uses Spring Security with form-based authentication:
- **Login URL:** `/login`
- **Logout URL:** `/logout`

### Default Credentials

⚠️ **Important:** Change these credentials in production!

- **Admin Account:** Use `reset_admin.sql` to set up admin credentials
- **User Accounts:** Register through the application UI

### Security Features

- ✅ CSRF Protection enabled
- ✅ Security headers configured
- ✅ Password encryption with BCrypt
- ✅ Session management
- ✅ Role-based authorization

### Testing Security

Security test files are provided:
- `csrf_test.html` - Test CSRF protection
- `security_headers_test.html` - Verify security headers

## 📁 Project Structure

```
Vintage/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/vintage/
│   │   │       ├── config/          # Security & app configuration
│   │   │       ├── controller/      # REST & MVC controllers
│   │   │       ├── model/           # JPA entities
│   │   │       ├── repository/      # Data access layer
│   │   │       ├── service/         # Business logic
│   │   │       └── VintageApplication.java
│   │   └── resources/
│   │       ├── static/              # CSS, JS, images
│   │       ├── templates/           # Thymeleaf templates
│   │       └── application.properties
│   └── test/                        # Unit & integration tests
├── uploads/                         # File upload directory
├── data/                            # Database files (H2)
├── Dockerfile                       # Docker configuration
├── docker-compose.yml               # Multi-container setup
├── Jenkinsfile                      # CI/CD pipeline
├── pom.xml                          # Maven dependencies
└── README.md

```

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test Class

```bash
./mvnw test -Dtest=YourTestClassName
```

### Test Coverage

```bash
./mvnw verify
```

## 🔄 CI/CD Pipeline

The project includes a Jenkins pipeline (`Jenkinsfile`) that automates:

1. **Build** - Compile and package the application
2. **Test** - Run unit and integration tests
3. **Quality Analysis** - Code quality checks
4. **Docker Build** - Create Docker image
5. **Deploy** - Deploy to target environment

### Setting up Jenkins

1. Install Jenkins with required plugins
2. Create a new Pipeline project
3. Point to this repository
4. Jenkins will automatically detect the `Jenkinsfile`

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow Java coding conventions
- Write meaningful commit messages
- Include unit tests for new features
- Update documentation as needed

## 📝 License

This project is private. All rights reserved.

## 👨‍💻 Author

**DaoTienDat2304**
- GitHub: [@DaoTienDat2304](https://github.com/DaoTienDat2304)

## 📞 Support

For issues, questions, or suggestions, please open an issue in the GitHub repository.

---

**Note:** This is a private repository. Ensure you have proper authorization before accessing or contributing to this project.