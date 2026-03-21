# variables
JC = javac
JVM = java
FLAGS = -d bin -sourcepath src
SRC_DIR = src/warehouse
BIN_DIR = bin/warehouse
MAIN_CLASS = warehouse.Simulation


default: classes

# compile all .java files in the warehouse directory
classes:
	@mkdir -p $(BIN_DIR)
	$(JC) $(FLAGS) $(SRC_DIR)/*.java

# start simulation
run: classes
	$(JVM) -cp bin $(MAIN_CLASS)

# clean up compiled files
clean:
	rm -rf $(BIN_DIR)/*