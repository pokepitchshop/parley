output "resource_group_name" {
  value = local.rg_name
}

output "acr_login_server" {
  value = azurerm_container_registry.acr.login_server
}

output "acr_name" {
  value = azurerm_container_registry.acr.name
}

output "key_vault_id" {
  value = azurerm_key_vault.kv.id
}

output "key_vault_uri" {
  value = azurerm_key_vault.kv.vault_uri
}

output "openai_endpoint" {
  value = azurerm_cognitive_account.openai.endpoint
}

output "openai_deployment_name" {
  value = azurerm_cognitive_deployment.llm.name
}

output "container_app_environment_id" {
  value = azurerm_container_app_environment.env.id
}

output "cosmos_account_name" {
  value = azurerm_cosmosdb_account.mongo.name
}

output "cosmos_mongo_database_name" {
  value = azurerm_cosmosdb_mongo_database.parley.name
}
