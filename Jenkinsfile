pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
metadata:
  namespace: githubble
spec:
  serviceAccountName: jenkins
  containers:
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command:
        - cat
      tty: true
      volumeMounts:
        - name: docker-config
          mountPath: /kaniko/.docker

    - name: kubectl
      image: alpine/k8s:1.30.0
      command:
        - cat
      tty: true

  volumes:
    - name: docker-config
      secret:
        secretName: dockerhub-secret
        items:
          - key: .dockerconfigjson
            path: config.json
"""
        }
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_REPO = 'jjwon1230/githubble-be'
        K8S_NAMESPACE = 'githubble'
        K8S_DEPLOYMENT = 'be'
        K8S_CONTAINER = 'app'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Image Tag') {
            steps {
                script {
                    env.IMAGE_TAG = env.BUILD_NUMBER
                    env.FULL_IMAGE = "${env.IMAGE_REPO}:${env.IMAGE_TAG}"
                }
                echo "IMAGE=${env.FULL_IMAGE}"
            }
        }

        stage('Build & Push Image') {
            steps {
                container('kaniko') {
                    sh """
                    /kaniko/executor \
                      --dockerfile=${WORKSPACE}/be/Dockerfile \
                      --context=${WORKSPACE}/be \
                      --destination=${FULL_IMAGE}
                    """
                }
            }
        }

        stage('Deploy') {
            steps {
                container('kubectl') {
                    sh """
                    kubectl apply -f be/infra/be-deployment.yaml -n ${K8S_NAMESPACE}
                    kubectl apply -f be/infra/be-service.yaml -n ${K8S_NAMESPACE}
                    kubectl set image deployment/${K8S_DEPLOYMENT} ${K8S_CONTAINER}=${FULL_IMAGE} -n ${K8S_NAMESPACE}

                    kubectl rollout status deployment/${K8S_DEPLOYMENT} -n ${K8S_NAMESPACE}
                    """
                }
            }
        }
    }
}