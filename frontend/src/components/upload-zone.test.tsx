import { render, screen } from "@testing-library/react";

import { UploadZone } from "@/components/upload-zone";

describe("UploadZone", () => {
  it("accepts project zip uploads", () => {
    const { container } = render(
      <UploadZone
        files={[]}
        onFilesSelected={() => {}}
        onRemove={() => {}}
        onAnalyze={() => {}}
        analyzing={false}
        validationError={null}
      />,
    );

    expect(screen.getAllByText(/project ZIP/i).length).toBeGreaterThan(0);
    const input = container.querySelector("input[type='file']");
    expect(input).not.toBeNull();
    expect(input?.getAttribute("accept")).toContain(".zip");
  });
});
