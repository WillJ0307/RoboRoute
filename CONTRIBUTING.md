There are 3 parts to RoboRoute:
- Android App located in `/app`
- NTOverAOA connection layer located in `/NTOverAOA`
- [Docusaurus](https://docusaurus.io/docs) Documentation located in `/docs`

## Android App
### What you'll need
- [Android Studio](https://developer.android.com/studio)

### Linting/Formating
We use [ktlint](https://ktlint.github.io/ktlint) to help with formatting and code quality
`ktlintCheck` will give a list of the issues, `ktlintFormat` will try to fix them

- **Run in Androdid Studio**: Select `ktlintFormat` or `ktlintCheck` from the run dropdown at the top middle ish of the window
- **Run via Command Line**:
  - Check: `./gradlew ktlintCheck`
  - Format: `./gradlew ktlintFormat`

> [!NOTE]
> always run `ktlintFormat` before commiting


## NTOverAOA
### What you'll need
- [Python](https://python.org/downloads)

### Git Submodules
NTOverAOA uses libwdi for easy driver installation on windows which needs to be built with the exe, it is setup as a git submodule to keep it up to date
```bash
git submodule update --init --recursive
```

### Installing Dependencies

```bash
pip install -r requirements.txt
```

### Linting/Formatting
We use [Ruff](https://docs.astral.sh/ruff/) to help with formatting and code quality

after installing the dependencies, in the terminal run 
```
ruff check
```
or
```
ruff format
```

> [!NOTE]
> Always run `ruff format` before commiting


## Documentation (Docusarus)
### What you'll need

- [Node.js](https://nodejs.org/en/download/) version 20.0 or above:

### Installing Dependencies

```bash
cd docs
npm install
```
### Writing Documentation
All documentation should be writen in .mdx files, within the `docs/docs` directory.
These docs use [Docusaurus](https://docusaurus.io/docs)

### Starting the Development Server
In the docs directory, run

```bash
npm run start
```

### If you have issues running the dev server
Try running this in the docs directory
```bash
npm run clear
```