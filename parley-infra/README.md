# parley-infra

Terraform for standing up **Parley** on Azure, organized by lifecycle layer.
State + runs live in HCP Terraform (org: `pokepitchshop-org`).

## Layers
- `foundation/` — resource group + the managed identity Parley runs as. Rarely changes.
- `platform/`   — ACR, Key Vault, Azure OpenAI (gpt-4o-mini), Container Apps environment.
- `app/`        — the Parley Container App (ingress, identity, env/secrets).

Each layer is its own state (its own HCP workspace). The `app` layer reads `platform`/`foundation`
outputs via `terraform_remote_state`. Environments (dev/staging/prod) are the **same code** applied
to different workspaces with different values — not separate folders.

## One-time bootstrap
1. Have an Azure subscription and run `az login`.
2. Set up Terraform -> Azure auth (an OIDC service principal, or ARM_* creds on the HCP workspaces).
3. In HCP Terraform (`pokepitchshop-org`) create three workspaces: `parley-foundation`, `parley-platform`, `parley-app`.
4. Put secrets (TWILIO_*, the Key Vault secret IDs) as **workspace variables** on `parley-app` — never in git.
5. Copy `environments/dev.tfvars.example` -> `dev.tfvars` and fill in `subscription_id`.

## Apply order (foundation -> platform -> app)
```
cd foundation && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../platform && terraform init && terraform apply -var-file=../environments/dev.tfvars
cd ../app      && terraform init && terraform apply -var-file=../environments/dev.tfvars
```

## After the app applies
`terraform output app_url` gives the public HTTPS base. Point Twilio's voice webhook to
`<app_url>/voice` — that's POK-11, and it retires the ngrok step (POK-10).

## Deploy the app image (from the parley/ repo)
```
./gradlew bootBuildImage --imageName=<acr_login_server>/parley:latest
az acr login --name <acr-name>
docker push <acr_login_server>/parley:latest
```
Then bump `var.image_tag` (or re-apply `app`) to roll a new revision.

## Spring AI
Use the `spring-ai-starter-model-azure-openai` starter. The Container App injects
`SPRING_AI_AZURE_OPENAI_*` env vars (relaxed binding -> `spring.ai.azure.openai.*`), so the app
needs no hardcoded endpoint/key. Keyless via the managed identity is the better end state.

## Notes / TODO
- Region defaults to `eastus2` (Azure OpenAI availability). Confirm model + version in your region.
- Verify the current `gpt-4o-mini` version in `platform/variables.tf`.
- Starter template: run `terraform fmt` and `terraform validate` in each layer before applying.
- For staging/prod, create matching HCP workspaces and apply with `staging.tfvars` / `prod.tfvars`.
