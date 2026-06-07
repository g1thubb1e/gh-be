pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_REPO = 'yaongmeow/githubble-be'
        K8S_NAMESPACE = 'githubble'
        K8S_DEPLOYMENT = 'be'
        K8S_CONTAINER = 'app'
        SSH_HOST = credentials('OCI_SERVER_2_HOST')
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
                withCredentials([usernamePassword(
                    credentialsId: 'SUE_DOCKER',
                    usernameVariable: 'ID',
                    passwordVariable: 'PW'
                )]) {
                    sh '''
                    echo "$PW" | docker login -u "$ID" --password-stdin
                    docker build -f Dockerfile -t "$FULL_IMAGE" .
                    docker push "$FULL_IMAGE"
                    docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                sshagent(credentials: ['OCI_SERVER_2']) {
                    sh '''
                    ssh -o StrictHostKeyChecking=no ubuntu@$SSH_HOST "sudo k3s kubectl apply -n '$K8S_NAMESPACE' -f -" < infra/be-deployment.yaml
                    ssh -o StrictHostKeyChecking=no ubuntu@$SSH_HOST "sudo k3s kubectl apply -n '$K8S_NAMESPACE' -f -" < infra/be-service.yaml
                    ssh -o StrictHostKeyChecking=no ubuntu@$SSH_HOST "
                      sudo k3s kubectl set image deployment/'$K8S_DEPLOYMENT' '$K8S_CONTAINER'='$FULL_IMAGE' -n '$K8S_NAMESPACE' &&
                      sudo k3s kubectl rollout status deployment/'$K8S_DEPLOYMENT' -n '$K8S_NAMESPACE'
                    "
                    '''
                }
            }
        }
    }
}
