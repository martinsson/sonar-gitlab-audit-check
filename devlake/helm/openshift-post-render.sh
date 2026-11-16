#!/bin/sh
# Helm post-renderer for OpenShift's restricted-v2 SCC: drops the fixed user, group and fsGroup 472
# that the Grafana sub-chart writes into its pod. Helm cannot unset them from values (a null for a
# sub-chart default is not applied), and restricted-v2 refuses any UID outside the namespace range.
#   helm upgrade --install devlake devlake/devlake --version 1.0.2 -f values-openshift.yaml \
#     --post-renderer ./openshift-post-render.sh
# Reads the rendered manifests on stdin, writes them on stdout.
exec sed -E '/^ *(runAsUser|runAsGroup|fsGroup): 472$/d'
