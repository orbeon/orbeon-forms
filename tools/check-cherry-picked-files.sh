#!/bin/bash

# Example: tools/check-cherry-picked-files.sh ../orbeon-forms/ "#779" "#5209"

other_dir="$1"
echo "Comparing files in current directory with files in '$other_dir'"

all_commits=""

# List all commits containing the given strings
for grep in "${@:2}"; do
  commits=$(git log --grep "$grep" | grep commit | sed "s/commit //g")
  echo "Commits for '$grep':"$'\n'"$commits"$'\n'
  if [ -n "$all_commits" ]; then
    all_commits+=$'\n'
  fi
  all_commits+="$commits"
done

all_files=""

# List all files in commits
while IFS= read -r commit; do
  files=$(git show --pretty="" --name-only "$commit")
  echo "Files for '$commit':"$'\n'"$files"$'\n'
  all_files+="$files"$'\n'
done <<< "$all_commits"

unique_files=$(echo "$all_files" | awk NF | sort | uniq)

files_with_diff=""
files_missing=""

# Compare each file with other directory/repository
while IFS= read -r file; do
  echo "$file vs $other_dir$file"
  diff_output=$(diff "$file" "$other_dir$file" 2>&1)
  exit_code="$?"
  # echo "$exit_code: $file";
  if [ "$exit_code" -eq 0 ]; then
    # No diff
    :
  elif [ "$exit_code" -eq 1 ]; then
    # File with difference
    files_with_diff+="$file"$'\n'
	elif [ "$exit_code" -eq 2 ]; then
		# File missing
		files_missing+="$file"$'\n'
  fi
done <<< "$unique_files"

echo ""

echo "Files with differences:"
echo "$files_with_diff"
echo ""

echo "Files missing:"
echo "$files_missing"
echo ""
