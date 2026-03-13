#!/bin/bash -l
# run-build.sh — Runs inside the Docker container as the travis user.
# Pre-processes .travis.yml, compiles ci.sh via travis-build, patches it, and runs the build.

set -e

rvm use 3.2.2

# =============================================================================
# Step 1: Pre-process .travis.yml
# Replace secure: entries with plaintext from .travis-env, remove openssl line
# =============================================================================

cp /home/travis/.travis.yml /home/travis/.travis.yml.work

ruby -e "
content = File.read('/home/travis/.travis.yml.work')
vars = File.readlines('/home/travis/.travis-env')
  .map(&:strip)
  .reject(&:empty?)
  .map { |v| '    - ' + v }
  .join(\"\n\")
new_content = content.gsub(/^([ \t]*- secure:.*\n)+/, vars + \"\n\")
new_content = new_content.gsub(/^.*openssl aes-256-cbc.*encrypted_cc6f6e239f95.*\n/, '')
File.write('/home/travis/.travis.yml.work', new_content)
"

echo "Pre-processed .travis.yml (replaced secure: entries, removed openssl line)"

# =============================================================================
# Step 2: Compile .travis.yml into ci.sh using travis-build
# =============================================================================

cd ~/.travis/travis-build

ruby -ryaml -rjson -e '
config = YAML.load_file("/home/travis/.travis.yml.work")
env = config.delete("env") || {}
config["global_env"] = env["global"] || []
config["env"] = ["TARGET=#{ENV["TRAVIS_TARGET"]}"]
puts({
  config: config,
  repository: {
    slug: ENV["TRAVIS_REPO_SLUG"],
    source_host: "github.com",
    private: true,
    vcs_type: "GithubRepository",
    github_id: 1
  },
  job: {
    id: 638298406,
    number: "1.1",
    branch: ENV["TRAVIS_BRANCH"],
    commit: ENV["TRAVIS_COMMIT"]
  },
  oauth_token: ENV["GITHUB_TOKEN"],
  build: { id: 277623752, number: "1" },
  prefer_https: true,
  keep_netrc: true
}.to_json)' | BUNDLE_GEMFILE=~/.travis/travis-build/Gemfile bundle _2.3.26_ exec ruby \
    ~/.travis/travis-build/script/compile > ~/ci.sh

echo "ci.sh compiled successfully ($(wc -l < ~/ci.sh) lines)"

# =============================================================================
# Step 3: Patch ci.sh
# =============================================================================

# Skip wait_for_network
sed -i 's/.*travis_cmd travis_wait_for_network.*/true # wait_for_network skipped/' ~/ci.sh

# Fix mkdir for .orbeon (directory already exists due to license mount)
sed -i 's/travis_cmd mkdir\\ \/home\/travis\/.orbeon /travis_cmd mkdir\\ -p\\ \/home\/travis\/.orbeon /' ~/ci.sh

echo "ci.sh patched successfully"

# =============================================================================
# Step 4: Run the build
# =============================================================================

source ~/.travis-env
exec bash ~/ci.sh
