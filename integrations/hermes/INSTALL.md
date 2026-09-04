# Install CodyOS Hermes Integration

1. Install and configure your own Hermes instance.
2. Enable Hermes' API server according to your Hermes version's documentation.
3. Create a backend API token locally.
4. Copy `config.example.env` to `.env` and set only your own local values.
5. Run the Cody Home Gateway with the matching `CODY_HOME_HERMES_*` environment variables, or import `codyos_hermes_integration` directly in a custom CodyOS service.

No Hermes core patch is required for the API-based integration.
