# Hosting the backend and frontend separately

The frontend will call this Java backend over HTTPS. They can live in different repositories and on different hosting providers.

## Recommended path with Azure for Students

Azure App Service can run this Spring Boot executable JAR using its managed Java SE runtime. A project Dockerfile is not required. See Microsoft's [Java deployment guide](https://learn.microsoft.com/en-us/azure/app-service/configure-language-java-deploy-run).

1. Activate [Azure for Students](https://azure.microsoft.com/en-us/free/students/) using your own Microsoft account and complete student verification.
2. Open the [Azure portal](https://portal.azure.com/), search for **Subscriptions**, and confirm that **Azure for Students** is active.
3. Open **App Services**, then **Create / Web App**. Select that subscription, a dedicated resource group such as `github-battle-rg`, and a unique application name.
4. Choose **Code**, **Java 17**, **Java SE**, and **Linux**. Start by checking whether **Free F1** is available in the selected region/subscription. Confirm the plan and estimated cost before creating anything; a paid plan uses student credit.
5. Build the backend with `./mvnw.cmd verify` on Windows. The deployment artifact is `target/github-battle-0.0.1-SNAPSHOT.jar`.
6. Deploy the JAR using the Azure CLI, Maven integration, or GitHub Actions. For an existing web app and an authenticated Azure CLI, the command is `az webapp deploy --resource-group github-battle-rg --name YOUR_APP_NAME --src-path target/github-battle-0.0.1-SNAPSHOT.jar --type jar`. Replace the placeholders with the actual resources. See [JAR deployment documentation](https://learn.microsoft.com/en-us/azure/app-service/deploy-zip#deploy-war-jar-or-ear-packages).
7. Configure `GITHUB_TOKEN` in the web app's environment when contribution metrics are required. Verify the managed runtime's port configuration, startup logs, and the public `/api/battles?left=octocat&right=torvalds` endpoint before connecting the frontend.

Account activation and resource creation are not completed by this guide. No Azure CLI was available on the local PATH when these instructions were prepared. Authentication and verification codes stay with the account owner.

Azure for Students provides $100 credit for 12 months under its eligibility terms. The App Service F1 plan has shared compute, 1 GB RAM, and 60 CPU minutes per day. Check [current limits](https://azure.microsoft.com/en-us/pricing/details/app-service/linux/) and plan availability during setup. A domain offer is separate from application hosting.

## An option for a free portfolio demo

- **Backend:** a Render web service using Docker to run the Spring Boot JAR. Render supports [Docker deployments](https://render.com/docs/docker) and a [Free instance type](https://render.com/docs/free).
- **Frontend:** a static frontend deployed to Netlify. Its [Free plan](https://docs.netlify.com/manage/accounts-and-billing/billing/billing-for-credit-based-plans/credit-based-pricing-plans/) has monthly usage limits.

Verified against provider documentation on September 10, 2026. Render Free services sleep after 15 minutes without traffic, so the next request can take around a minute to wake the backend. Netlify's current credit-based Free plan includes 300 credits per month with a hard limit. Review current provider terms before deploying; free plans suit a demonstration with limited traffic.

## Work to do when deploying

1. Add and validate a Dockerfile that builds the application and runs its JAR with Java 17 or newer. Keep `.env` and other local secrets out of the image and build context.
2. Create a Render Docker web service from the backend repository and select its Free instance type.
3. Set `GITHUB_TOKEN` in the backend service's environment if contribution metrics are needed. The app already reads the hosting platform's `PORT` environment variable.
4. Deploy the separate frontend on Netlify. Configure its API base URL to use the backend's public HTTPS address, not `localhost`.
5. Add a CORS allowlist in the backend for the exact deployed frontend origin. CORS is not enabled in the current backend; it does not affect Postman or server-to-server requests.
6. Verify a comparison from the deployed browser frontend, including the backend wake-up/loading and error states.

Example future URL (placeholder, not an existing deployment):

```text
https://your-backend.onrender.com/api/battles?left=octocat&right=torvalds
```

The frontend needs only the backend URL. `GITHUB_TOKEN` stays on the Java server and must never be put into frontend source or a public build-time variable. The backend currently needs no database; storing permanent battle results would require a separate persistence design.

No cloud service has been created or deployed by adding this document.
