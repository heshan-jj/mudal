<!-- Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE. -->
# Mudal Web - SMS Parser Tool

A web-based tool for testing and reporting SMS parsing issues for the Mudal app.

## Features

- Test SMS parsing with instant feedback
- Support for 30+ Indian banks
- Report parsing issues to improve accuracy
- Dark theme UI with responsive design

## Prerequisites

- Java 17 or higher
- PostgreSQL database (or Supabase account)

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/heshan-jj/mudal.git
   cd mudal/pennywise-web/server
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env.local
   # Edit .env.local with your database password
   ```

3. **Build the application**
   ```bash
   cd ../
   ./gradlew :server:shadowJar
   ```

4. **Run the application**
   ```bash
   DATABASE_PASSWORD=your_password java -jar server/build/libs/server-all.jar
   ```

   The application will be available at `http://localhost:8080`

## Database Configuration

The application uses PostgreSQL for storing SMS parsing reports. Configure the database connection in `src/main/resources/application.yaml`:

```yaml
postgres:
  url: jdbc:postgresql://your-database-host:5432/your-database
  user: your-database-user
  password: ${DATABASE_PASSWORD}
```

The password is read from the `DATABASE_PASSWORD` environment variable for security.

## Production Deployment

### Using Docker

```bash
# Build the Docker image
docker build -t pennywise-web .

# Run the container
docker run -p 8080:8080 -e DATABASE_PASSWORD=your_password pennywise-web
```

### Using systemd (Linux)

1. Create a service file at `/etc/systemd/system/pennywise-web.service`:
   ```ini
   [Unit]
   Description=Mudal Web SMS Parser
   After=network.target

   [Service]
   Type=simple
   User=www-data
   WorkingDirectory=/opt/pennywise-web
   Environment="DATABASE_PASSWORD=your_password"
   ExecStart=/usr/bin/java -jar /opt/pennywise-web/server-all.jar
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```

2. Start the service:
   ```bash
   sudo systemctl enable pennywise-web
   sudo systemctl start pennywise-web
   ```

## API Endpoints

- `GET /` - Main web interface
- `POST /htmx/parse` - Parse SMS (HTMX endpoint)
- `POST /api/report` - Submit parsing issue report
- `GET /static/*` - Static resources

## Security Notes

- Database credentials are stored in environment variables
- Never commit `.env.local` or any file containing actual credentials
- Use HTTPS in production with a reverse proxy (nginx/caddy)

## Contributing

Please report any issues or submit pull requests to improve the parser accuracy.

## License

MIT License - see LICENSE file in the root directory