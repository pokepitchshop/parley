output "app_url" {
  description = "Public HTTPS base. Set Twilio's voice webhook to <app_url>/voice (POK-11)."
  value       = "https://${azurerm_container_app.parley.ingress[0].fqdn}"
}
