let studentSystem = {
    showDetails: function (name, rollNo, course) {
        console.log("Name    :", name);
        console.log("Roll No :", rollNo);
        console.log("Course  :", course);
    },
    assignGrade: function (percentage) {
        let grade;

        if (percentage >= 85) grade = "A";
        else if (percentage >= 70) grade = "B";
        else if (percentage >= 50) grade = "C";
        else grade = "Fail";

        console.log("Grade           :", grade);
    }
};

studentSystem.showDetails("Yash", 1 , "B.Tech CSE");

studentSystem.assignGrade(percent);
