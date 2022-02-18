def GetScmProjectName() {
    return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.")[0]
}

pipeline {
    agent any
    environment {
        GITHUB_TOKEN = credentials('9e494763-394a-4837-a25f-c1e9e61a7289')
        repo         = GetScmProjectName()
        version      = 'nightly'
        dist         = '9.2.0.0-SNAPSHOT'
        username     = 'HiromuHota'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -DskipTests clean source:jar package'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
        stage('Deliver') {
            steps {
                sh '''
                    github-release delete --user $username --repo $repo --tag webspoon/$version || true
                    git tag -f webspoon/$version
                    git push -f https://${GITHUB_TOKEN}@github.com/$username/$repo.git webspoon/$version
                    github-release release --user $username --repo $repo --tag webspoon/$version --name "webSpoon/$version" --description "Auto-build by Jenkins on $(date +'%F %T %Z')" --pre-release
                    github-release upload --user $username --repo $repo --tag webspoon/$version --name "pdi-dataservice-server-plugin-$dist.jar" --file impl/target/pdi-dataservice-server-plugin-$dist.jar
                    github-release upload --user $username --repo $repo --tag webspoon/$version --name "pdi-dataservice-server-plugin-$dist-sources.jar" --file impl/target/pdi-dataservice-server-plugin-$dist-sources.jar
                '''
            }
        }
    }
}
