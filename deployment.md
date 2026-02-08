
> **single chart → multi-component (backend + frontend) application**

Below is a **clean, production-ready way** to do this for **`costanalysis-demo`** on AKS, while keeping Vault / CSI / HPA / security patterns intact.

I’ll give you:

1. **Target architecture**
2. **Chart structure**
3. **Refactored `values.yaml`**
4. **Backend + frontend Deployments**
5. **Service + Ingress**
6. **Postgres integration**
7. **What stays reused vs new**

---

## 1. Target architecture (AKS)

```
costanalysis-demo
├── backend (Spring Boot)
│   ├── Deployment
│   ├── Service (ClusterIP)
│   ├── HPA
│   └── Vault / CSI secrets
│
├── frontend (React / NGINX)
│   ├── Deployment
│   ├── Service (ClusterIP)
│   └── Ingress (public)
│
└── PostgreSQL
    └── External (existing DB, not deployed)
```

Key assumptions:

* Backend is **internal-only**
* Frontend is **public**
* React app calls backend via **internal service DNS**
* Secrets come from **Vault + Azure Key Vault CSI**
* No Redis, no stateful workloads

---

## 2. Chart structure (single Helm chart)

```
costanalysis-demo/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── backend/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── hpa.yaml
│   ├── frontend/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── ingress.yaml
│   ├── vault-agent-configmap.yaml
│   ├── csi-config.yaml
│   ├── csi-secret-provider.yaml
│   └── _helpers.tpl
```

👉 One chart, two components, shared helpers.

---

## 3. Refactored `values.yaml`

This is the **most important part**.

```yaml
app:
  name: costanalysis-demo
  namespace: orchestration-eventservice

global:
  labels:
    azure.workload.identity/use: "true"

backend:
  enabled: true
  replicaCount: 1

  image:
    repository: release-container-registry.home.net/maverick/costanalysis-backend
    tag: 1.0.0
    pullPolicy: IfNotPresent

  port: 8080
  service:
    type: ClusterIP

  runtime:
    type: java
  java:
    jarPath: /app/costanalysis-demo.jar
    properties:
      - "-Dspring.profiles.active=prod"

  resources:
    requests:
      cpu: 250m
      memory: 1000Mi
    limits:
      memory: 1000Mi

  autoscaling:
    enabled: true
    minReplicas: 1
    maxReplicas: 3
    averageRelativeCPU: 60
    averageRelativeMemory: 80

  env:
    - name: SPRING_DATASOURCE_URL
      value: "jdbc:postgresql://postgres.example.com:5432/costdb"
    - name: SPRING_DATASOURCE_USERNAME
      value: costuser

frontend:
  enabled: true
  replicaCount: 2

  image:
    repository: release-container-registry.home.net/maverick/costanalysis-frontend
    tag: 1.0.0
    pullPolicy: IfNotPresent

  port: 80
  service:
    type: ClusterIP

  env:
    - name: REACT_APP_API_BASE_URL
      value: "http://costanalysis-demo-backend:8080"

  ingress:
    enabled: true
    className: nginx
    path: /
    hosts:
      - costanalysis-demo.company.com
    tls:
      enabled: true
      secretName: costanalysis-demo-tls

vault:
  enabled: true
  role: costanalysis-demo
  resource: https://management.azure.com/
  image: container-registry.home.net/hashicorp/vault:1.14.3

  secrets:
    generic:
      SPRING_DATASOURCE_PASSWORD: postgres/password

csi:
  enabled: true
  csiMount: /etc/csi_secrets
  parameters:
    tenantId: MaverickCloud.onmicrosoft.com
    keyvaultName: costanalysis-kv
```

---

## 4. Backend Deployment (Spring Boot)

`templates/backend/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-backend
  labels:
    app: {{ .Values.app.name }}
    component: backend
spec:
  replicas: {{ .Values.backend.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Values.app.name }}
      component: backend
  template:
    metadata:
      labels:
        app: {{ .Values.app.name }}
        component: backend
{{ toYaml .Values.global.labels | nindent 8 }}
    spec:
      serviceAccountName: {{ include "vault.serviceAccountName" . }}

      initContainers:
{{- if .Values.vault.enabled }}
{{ include "vault_agent_init_container" . | nindent 8 }}
{{- end }}

      containers:
        - name: backend
          image: {{ .Values.backend.image.repository }}:{{ .Values.backend.image.tag }}
          imagePullPolicy: {{ .Values.backend.image.pullPolicy }}

          command:
            - bash
            - -c
            - |
              source /etc/secrets/secrets_eva.sh
              exec java {{ range .Values.backend.java.properties }}{{ . }} {{ end }}-jar {{ .Values.backend.java.jarPath }}

          ports:
            - containerPort: {{ .Values.backend.port }}

          env:
{{- range .Values.backend.env }}
            - name: {{ .name }}
              value: {{ .value | quote }}
{{- end }}

          resources:
{{ toYaml .Values.backend.resources | nindent 12 }}

          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {{ .Values.backend.port }}

          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {{ .Values.backend.port }}

          volumeMounts:
            - name: vault-secrets
              mountPath: /etc/secrets

      volumes:
        - name: vault-secrets
          emptyDir: {}
```

---

## 5. Frontend Deployment (React / NGINX)

`templates/frontend/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}-frontend
spec:
  replicas: {{ .Values.frontend.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Values.app.name }}
      component: frontend
  template:
    metadata:
      labels:
        app: {{ .Values.app.name }}
        component: frontend
    spec:
      containers:
        - name: frontend
          image: {{ .Values.frontend.image.repository }}:{{ .Values.frontend.image.tag }}
          imagePullPolicy: {{ .Values.frontend.image.pullPolicy }}

          ports:
            - containerPort: {{ .Values.frontend.port }}

          env:
{{- range .Values.frontend.env }}
            - name: {{ .name }}
              value: {{ .value | quote }}
{{- end }}

          livenessProbe:
            httpGet:
              path: /
              port: {{ .Values.frontend.port }}
```

---

## 6. Services + Ingress

### Backend service (internal)

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}-backend
spec:
  type: ClusterIP
  selector:
    app: {{ .Values.app.name }}
    component: backend
  ports:
    - port: {{ .Values.backend.port }}
```

### Frontend ingress (public)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ .Release.Name }}-frontend
spec:
  ingressClassName: {{ .Values.frontend.ingress.className }}
  tls:
    - secretName: {{ .Values.frontend.ingress.tls.secretName }}
      hosts:
{{- range .Values.frontend.ingress.hosts }}
        - {{ . }}
{{- end }}
  rules:
{{- range .Values.frontend.ingress.hosts }}
    - host: {{ . }}
      http:
        paths:
          - path: {{ $.Values.frontend.ingress.path }}
            pathType: Prefix
            backend:
              service:
                name: {{ $.Release.Name }}-frontend
                port:
                  number: {{ $.Values.frontend.port }}
{{- end }}
```

---

## 7. PostgreSQL integration (no Redis)

* DB **not deployed**
* Credentials from Vault
* Connection string built via Spring config
* No StatefulSet, no PVCs

This is exactly the right approach for Azure-managed DBs.

---

## 8. What you reused vs added

### Reused ♻️

* Vault agent initContainer
* CSI secret provider
* HPA pattern
* Security contexts
* Workload identity
* Helm helper patterns
* Autoscaling v2

### Added ➕

* Multi-component chart layout
* Frontend deployment + ingress
* Backend/Frontend separation
* Runtime abstraction
* Clear DB-only backend dependency

---

## Final take

This design:

* Scales cleanly
* Is AKS-native
* Avoids chart sprawl
* Is future-proof (you can add more components later)
* Keeps secrets **out of images and values**

If you want, next I can:

* Generate a **full repo-ready chart**
* Add **blue/green or canary support**
* Add **backend-only private ingress**
* Add **Azure App Gateway Ingress Controller specifics**

Just tell me where you want to go next.
