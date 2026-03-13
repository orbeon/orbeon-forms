FROM travisci/ci-ubuntu-2004:packer-1761749916-4ffea187-dirty

# Free up disk space
RUN rm -rf \
    /usr/local/clang-* \
    /usr/local/lib/jvm/openjdk8 \
    /usr/local/lib/jvm/openjdk9 \
    /usr/local/lib/jvm/openjdk10 \
    /usr/local/lib/jvm/openjdk12 \
    /usr/local/lib/jvm/openjdk13 \
    /usr/local/lib/jvm/openjdk14 \
    /usr/local/lib/jvm/openjdk15 \
    /usr/local/lib/jvm/openjdk16 \
    /usr/local/lib/jvm/openjdk17 \
    /opt/at14.0 \
    /opt/python \
    /opt/google \
    /opt/pyenv \
    /opt/couchdb \
    /opt/perforce \
    /usr/local/firefox* \
    /usr/local/chromium* \
    /usr/local/google-chrome* \
    /usr/local/phantomjs* \
    /usr/local/lib/heroku \
    /usr/local/gradle-* \
    /usr/local/cmake-* \
    /usr/local/docker-* \
    /usr/local/android-sdk \
    /usr/local/packer-* \
    /usr/local/groovy-* \
    /usr/local/shellcheck-* \
    /usr/share/dotnet \
    /home/travis/.rvm/rubies/ruby-2.* \
    /home/travis/.rvm/rubies/ruby-3.0.* \
    /home/travis/.rvm/rubies/ruby-3.1.* \
    /home/travis/.rvm/src \
    /home/travis/.rvm/archives/* \
    /home/travis/.phpenv \
    /home/travis/.nvm/versions \
    /home/travis/.kiex \
    /home/travis/perl5 \
    /home/travis/otp \
    /home/travis/virtualenv \
    /home/travis/gopath \
    /usr/local/go* \
    /usr/local/rustup \
    /usr/local/cargo \
    /var/lib/apt/lists/* \
    /tmp/* \
    /var/tmp/* \
 && rm -f /home/travis/.bash_profile.d/pyenv.bash \
 && sed -i "s/_JAVA_OPTIONS='-Xmx2048m -Xms512m'/_JAVA_OPTIONS='-Xmx4g -Xms512m'/" \
        /home/travis/.bash_profile.d/travis_jdk.bash

# Install travis-build
USER travis
WORKDIR /home/travis
RUN bash -lc '\
    rvm install 3.2.2 && \
    rvm use 3.2.2 && \
    gem install bundler:2.3.26 && \
    git clone https://github.com/travis-ci/travis-build.git ~/.travis/travis-build && \
    cd ~/.travis/travis-build && \
    BUNDLE_GEMFILE=~/.travis/travis-build/Gemfile bundle _2.3.26_ install'

COPY --chown=travis:travis run-build.sh /home/travis/run-build.sh
RUN chmod +x /home/travis/run-build.sh

USER root
CMD ["/sbin/init"]
