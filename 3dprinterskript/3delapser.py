import sys
import re

def manipulate_gcode(input_file, output_file):
    with open(input_file, 'r') as infile, open(output_file, 'w') as outfile:
        for line in infile:
            outfile.write(line)
            if line.startswith(';layer:'):
                # Insert custom commands after each layer
                outfile.write("G91\n")          # Use relative positioning mode
                outfile.write("G1 E-5 F1500\n")       # Retract -5mm
                outfile.write("G1 Z2 F420\n")        # Move Z up 2mm
                outfile.write("G90\n")          # Use absolute positioning mode
                outfile.write("G1 X65 F4500\n")   # Park head
                outfile.write("G1 Y65 F4500\n")   # Park bed
                outfile.write("G4 S5\n")        # Wait 5 seconds
                outfile.write("G91\n")          # Use relative positioning mode
                outfile.write("G1 Z-2 F420\n")       # Z down 2mm
                outfile.write("G1 E5 F1500\n")        # Prime 5mm
                outfile.write("G90\n")          # Go back to absolute position mode for all axes
def main():
    if len(sys.argv) != 2:
        print("Usage: python 3delapser.py <input_gcode_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = input_file.replace('.gcode', '_mod.gcode')

    manipulate_gcode(input_file, output_file)
    print(f"Modified G-code saved to: {output_file}")

if __name__ == "__main__":
    main()
